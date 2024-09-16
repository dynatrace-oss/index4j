/*
 * Copyright 2024 Dynatrace LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 *
 * This file includes a Java port along with modifications of the Fixed-Block boosting Wavelet tree
 * algorithm originally published at https://github.com/dominikkempa/faster-minuter under the following license:
 *
 * Copyright 2015-2021 Dominik Kempa, Juha Karkkainen
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.dynatrace.wavelet;

import static com.dynatrace.serialization.Serialization.checkSerialVersion;
import static com.dynatrace.wavelet.Arrays.maxValueOfArray;

import com.dynatrace.bitsequence.RrrVector;
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.LongArrayBitVector;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import javax.annotation.concurrent.ThreadSafe;

/**
 * This class implements fixed-block boosting wavelet trees. It also uses Huffman encoding for the
 * symbols. It is extremely efficient in comparison to the Wavelet matrix or the Wavelet tree, while
 * retaining the same speed as the wavelet matrix.
 *
 * <p>The code was adapted to enable up to 32,767 symbols. This could be extended to support 65,536.
 *
 * <p>Note that the C++ version is designed for 256 symbols. The encoding of the blocks was also
 * changed. Instead of using 3, 4 and 2 bytes for storing tree information, symbol with boundary
 * information and navigation information, now we require 4, 5 and 2 bytes, respectively.
 *
 * <p>The source code used here is adapted from:
 *
 * <ul>
 *   <li><a
 *       href="https://github.com/dominikkempa/faster-minuter">https://github.com/dominikkempa/faster-minuter</a>
 * </ul>
 */
@ThreadSafe
public final class WaveletFixedBlockBoosting {

    private static final byte SERIAL_VERSION_V0 = 0;
    // These private static variables correspond to the defines
    // minimally increases space but removes navigational queries, default: ON
    // private static final boolean ADD_NAVIGATIONAL_BLOCK_HEADER = true;
    // reduces the space at the cost of rank query slowdown, default: OFF
    // private static final boolean SPARSE_SUPERBLOCK_MAPPING = false;
    // permits different blocks sizes, reduces time and space, default: ON
    // private static final boolean ALLOW_VARIABLE_BLOCK_SIZE = true;
    // use fast computation of block sizes, default: ON
    // private static final boolean FAST_CONSTRUCTION = true;
    private static final long T_SBS_LOG = 20;
    // class sizes
    private static final int BLOCK_HEADER_ITEM_SIZE = Integer.BYTES * 3 + 2; // 3 ints and 2 bytes
    // members
    private static final long SUPER_BLOCK_SIZE_LOG = T_SBS_LOG;
    private static final long SUPER_BLOCK_SIZE = (1L << T_SBS_LOG);
    private static final long HYPER_BLOCK_SIZE = (1L << 32);
    static final Comparator<Tuple<Long, List<Short>>>
            INVERTED_PRIORITY_QUEUE_COMPARATOR_FOR_HUFFMAN =
                    getInvertedPriorityQueueComparatorForHuffman();
    private static final Comparator<Tuple<Long, Short>> LONG_SHORT_TUPLE_COMPARATOR =
            getLongShortTupleComparator();
    private final long size;
    private final int alphabetSize;
    private final long[] count;
    private final long[] hyperBlockRank; // ranks at hyperblock boundary
    private final int[] superBlockRank; // ranks at superblock boundary
    private final short[] globalMapping; // mapping from global alphabet to superblock alphabet
    private final SuperBlockHeaderItem[] superBlockHeaderItems; // superblock headers
    private final int samplingRateRrr;

    /**
     * Builds a Wavelet tree with the fixed block boosting technique with a default {@code
     * sampleRate} of 64.
     *
     * @param text The input text already mapped to a monotonic sequence of integers
     */
    public WaveletFixedBlockBoosting(short[] text) {
        this(text, 64);
    }

    /**
     * Builds a Wavelet tree with the fixed block boosting technique.
     *
     * @param text The input text already mapped to a monotonic sequence of integers
     * @param samplingRate The sampling rate used for the bit vectors, see {@link RrrVector}
     */
    public WaveletFixedBlockBoosting(short[] text, int samplingRate) {
        this.size = text.length;
        this.samplingRateRrr = samplingRate;
        this.alphabetSize = maxValueOfArray(text) + 1;
        this.count = new long[this.alphabetSize];
        long numSuperBlocks = (size + SUPER_BLOCK_SIZE - 1) / SUPER_BLOCK_SIZE;
        long numHyperBlocks = (size + HYPER_BLOCK_SIZE - 1) / HYPER_BLOCK_SIZE;

        // Allocate headers.
        hyperBlockRank = new long[(int) (numHyperBlocks * this.alphabetSize)];
        superBlockRank = new int[(int) (numSuperBlocks * this.alphabetSize)];
        globalMapping = new short[(int) (numSuperBlocks * this.alphabetSize)];
        for (int i = 0; i < numSuperBlocks * this.alphabetSize; i++) {
            globalMapping[i] = (short) (this.alphabetSize - 1);
        }
        superBlockHeaderItems = new SuperBlockHeaderItem[(int) numSuperBlocks];
        for (int i = 0; i < numSuperBlocks; i++) {
            superBlockHeaderItems[i] = new SuperBlockHeaderItem();
        }

        for (long superBlockId = 0; superBlockId < numSuperBlocks; ++superBlockId) {
            long superBlockBeg = superBlockId * SUPER_BLOCK_SIZE;
            encodeSuperBlock(text, superBlockBeg, superBlockId);
        }
    }

    /**
     * Builds a Wavelet tree with the fixed block boosting technique with a default {@code
     * sampleRate} of 64.
     *
     * @param text The input text (note that if mapped to a monotonic sequence of integers, the
     *     supported charset is the size of the alphabet, until 65,536 - otherwise it only supports
     *     until one char exceeds 65,536)
     */
    public WaveletFixedBlockBoosting(char[] text) {
        this(text, 64);
    }

    /**
     * Builds a Wavelet tree with the fixed block boosting technique.
     *
     * @param text The input text (note that if mapped to a monotonic sequence of integers, the
     *     supported charset is the size of the alphabet, until 65,536 - otherwise it only supports
     *     until one char exceeds 65,536)
     * @param samplingRate The sampling rate used for the bit vectors, see {@link RrrVector}
     */
    public WaveletFixedBlockBoosting(char[] text, int samplingRate) {

        if (text.length == 0) {
            throw new IllegalArgumentException("Input length must be > 0");
        }

        this.size = text.length;
        this.samplingRateRrr = samplingRate;
        this.alphabetSize = maxValueOfArray(text) + 1;
        this.count = new long[this.alphabetSize];

        long numSuperBlocks = (size + SUPER_BLOCK_SIZE - 1) / SUPER_BLOCK_SIZE;
        long numHyperBlocks = (size + HYPER_BLOCK_SIZE - 1) / HYPER_BLOCK_SIZE;

        // Allocate headers.
        hyperBlockRank = new long[(int) (numHyperBlocks * this.alphabetSize)];
        superBlockRank = new int[(int) (numSuperBlocks * this.alphabetSize)];
        globalMapping = new short[(int) (numSuperBlocks * this.alphabetSize)];
        for (int i = 0; i < numSuperBlocks * this.alphabetSize; i++) {
            globalMapping[i] = (short) (this.alphabetSize - 1);
        }
        superBlockHeaderItems = new SuperBlockHeaderItem[(int) numSuperBlocks];
        for (int i = 0; i < numSuperBlocks; i++) {
            superBlockHeaderItems[i] = new SuperBlockHeaderItem();
        }
        // map to short without monotonicity
        short[] mappedText = new short[text.length];
        for (int i = 0; i < text.length; i++) {
            mappedText[i] = (short) text[i];
        }

        for (long superBlockId = 0; superBlockId < numSuperBlocks; ++superBlockId) {
            long superBlockBeg = superBlockId * SUPER_BLOCK_SIZE;
            encodeSuperBlock(mappedText, superBlockBeg, superBlockId);
        }
    }

    private WaveletFixedBlockBoosting(
            long size,
            int alphabetSize,
            int samplingRateRrr,
            long[] count,
            long[] hyperBlockRank,
            int[] superBlockRank,
            short[] globalMapping,
            SuperBlockHeaderItem[] sbhi) {
        this.size = size;
        this.alphabetSize = alphabetSize;
        this.samplingRateRrr = samplingRateRrr;
        this.count = count;
        this.hyperBlockRank = hyperBlockRank;
        this.superBlockRank = superBlockRank;
        this.globalMapping = globalMapping;
        this.superBlockHeaderItems = sbhi;
    }

    private static long computeSymbolFromBlockHeader(
            byte[] blockHeader, int blockHeaderPtr, long code, long codeLength) {

        long blockC = 0;
        long tempCode = 0;
        for (long i = 1; i < codeLength; ++i) {
            byte b0 = blockHeader[blockHeaderPtr];
            byte b1 = blockHeader[blockHeaderPtr + 1];
            long levelLeafCount = ((b1 << 8) & 0x00ff00 | (b0) & 0x0000ff);
            blockHeaderPtr += 4;
            tempCode += levelLeafCount;
            blockC += levelLeafCount;
            tempCode <<= 1;
        }
        blockC += code - tempCode;
        return blockC;
    }

    private static long restoreCodeFromBlockHeader(
            long blockC, byte[] blockHeaderPtr, int posStartInBlockHeaderPtr, long treeHeight) {

        int code = 0;
        int codeLength = 1;

        long leafCount = 0;
        while (codeLength < treeHeight) {
            code <<= 1;
            byte b0 = blockHeaderPtr[posStartInBlockHeaderPtr];
            byte b1 = blockHeaderPtr[posStartInBlockHeaderPtr + 1];
            long levelLeafCount = ((b1 << 8) & 0x00ff00 | (b0) & 0x0000ff);

            if (leafCount + levelLeafCount > blockC) {
                code += (blockC - leafCount);
                break;
            } else {
                code += levelLeafCount;
                ++codeLength;
                leafCount += levelLeafCount;
                posStartInBlockHeaderPtr += 4;
            }
        }
        if (codeLength == treeHeight) {
            code <<= 1;
            code += (blockC - leafCount);
        }
        return (((long) code) << 32) | codeLength;
    }

    /**
     * Deserializes a {@code WaveletFixedBlockBoosting} from a {@code ObjectInput} stream.
     *
     * @param objectInput The stream from which to read from
     * @return The deserialized instance of this object
     */
    public static WaveletFixedBlockBoosting read(ObjectInput objectInput) throws IOException {
        checkSerialVersion(SERIAL_VERSION_V0, objectInput.readByte());
        long size = objectInput.readLong();
        int alphabetSize = objectInput.readInt();
        int samplingRateRrr = objectInput.readInt();

        long[] count = new long[objectInput.readInt()];
        for (int i = 0; i < count.length; i++) {
            count[i] = objectInput.readLong();
        }
        long[] hyperBlockRank = new long[objectInput.readInt()];
        for (int i = 0; i < hyperBlockRank.length; i++) {
            hyperBlockRank[i] = objectInput.readLong();
        }
        int[] superBlockRank = new int[objectInput.readInt()];
        for (int i = 0; i < superBlockRank.length; i++) {
            superBlockRank[i] = objectInput.readInt();
        }
        short[] globalMapping = new short[objectInput.readInt()];
        for (int i = 0; i < globalMapping.length; i++) {
            globalMapping[i] = objectInput.readShort();
        }
        SuperBlockHeaderItem[] sbhi = new SuperBlockHeaderItem[objectInput.readInt()];
        for (int i = 0; i < sbhi.length; i++) {
            sbhi[i] = SuperBlockHeaderItem.read(objectInput);
        }

        return new WaveletFixedBlockBoosting(
                size,
                alphabetSize,
                samplingRateRrr,
                count,
                hyperBlockRank,
                superBlockRank,
                globalMapping,
                sbhi);
    }

    private void computeSymbolFreq(short[] text, int from, int textLength, long[] frequency) {

        for (int i = 0; i < this.alphabetSize; i++) {
            frequency[i] = 0L;
        }
        for (long i = 0; i < textLength; ++i) {
            frequency[text[(int) i + from]] += 1;
        }
    }

    private void computeHuffmanCodeLengths(long[] frequency, long[] codeLength) {

        Arrays.fill(codeLength, 0L);
        Queue<Tuple<Long, List<Short>>> pq =
                new PriorityQueue<>(INVERTED_PRIORITY_QUEUE_COMPARATOR_FOR_HUFFMAN);

        for (long i = 0; i < this.alphabetSize; ++i) {
            if (frequency[(int) i] > 0) {
                List<Short> v = new ArrayList<>();
                v.add((short) i);
                Tuple<Long, List<Short>> toInsert =
                        new Tuple<Long, List<Short>>(frequency[((int) i)], v);
                pq.add(toInsert);
            }
        }

        while (pq.size() > 1) {
            Tuple<Long, List<Short>> x = pq.poll();
            Tuple<Long, List<Short>> y = pq.poll();
            List<Short> v = x.value;
            v.addAll(y.value);
            for (short i : v) {
                ++codeLength[i];
            }
            pq.add(new Tuple<Long, List<Short>>(x.key + y.key, v));
        }
    }

    private void encodeBlocksInSuperblock(
            short[] text, long superBlockPtr, long superBlockId, long blockSizeLog) {

        SuperBlockHeaderItem superBlockHeaderItem = superBlockHeaderItems[(int) superBlockId];

        long blockSize = (1L << blockSizeLog);
        long superBlockBeg = superBlockId * SUPER_BLOCK_SIZE;
        long superBlockEnd = Math.min(superBlockBeg + SUPER_BLOCK_SIZE, size);
        long superBlockSize = superBlockEnd - superBlockBeg;
        long superBlockSigma = (long) superBlockHeaderItem.sigma + 1;
        int[] onesCount = new int[1];
        superBlockHeaderItem.blockSizeLog = (short) blockSizeLog;

        // Allocate the array storing mapping
        // from superblock alphabet to block alphabets.
        superBlockHeaderItem.mapping =
                new short
                        [(int)
                                (superBlockSigma
                                        * (WaveletFixedBlockBoosting.SUPER_BLOCK_SIZE
                                                / blockSize))]; // , 255);
        for (int i = 0;
                i < superBlockSigma * (WaveletFixedBlockBoosting.SUPER_BLOCK_SIZE / blockSize);
                i++) {
            superBlockHeaderItem.mapping[i] = (short) (this.alphabetSize - 1);
        }

        // Fill in the fixed-size block header and superblock headers, and
        // compute sizes of variable-size block headers and bitvectors.
        long superBlockBvSize = 0;
        long variableBlockHeaderSize = 0;

        long blocksInThisSuperBlock = (superBlockSize + blockSize - 1) / blockSize;
        superBlockHeaderItem.blockHeaders = new BlockHeaderItem[(int) blocksInThisSuperBlock];
        for (int i = 0; i < blocksInThisSuperBlock; i++) {
            superBlockHeaderItem.blockHeaders[i] = new BlockHeaderItem();
        }

        for (long blockId = 0; blockId < blocksInThisSuperBlock; ++blockId) {
            BlockHeaderItem blockHeader = superBlockHeaderItem.blockHeaders[(int) blockId];
            long blockBeg = blockId * blockSize;
            long blockEnd = Math.min(blockBeg + blockSize, superBlockSize);
            long thisBlockSize = blockEnd - blockBeg;
            int blockPtr = (int) (superBlockPtr + blockBeg); // references var text

            // Compute global-to-block symbol mapping and basic
            // information about symbol distribution in the block.
            long treeHeight;
            long bvSize = 0;
            long sigma;
            List<Short> globalToBlockMapping = new ArrayList<>(this.alphabetSize);
            for (int i = 0; i < this.alphabetSize; i++) {
                globalToBlockMapping.add((short) this.alphabetSize);
            }

            // Compute Huffman code lengths.
            long[] frequencies = new long[this.alphabetSize];
            long[] codeLength = new long[this.alphabetSize];
            computeSymbolFreq(text, blockPtr, (int) thisBlockSize, frequencies);
            computeHuffmanCodeLengths(frequencies, codeLength);

            // Sort symbols by frequency.
            List<Tuple<Long, Short>> sym = new ArrayList<>();
            for (long i = 0; i < this.alphabetSize; ++i) {
                if (frequencies[((int) i)] > 0) {
                    sym.add(new Tuple<>(codeLength[((int) i)], (short) i));
                }
            }

            sym.sort(LONG_SHORT_TUPLE_COMPARATOR);

            // Fill in all fields.
            sigma = sym.size();
            treeHeight = -1;
            for (Long currentCodeLength : codeLength) {
                if (currentCodeLength > treeHeight) {
                    treeHeight = currentCodeLength;
                }
            }
            for (long i = 0; i < sym.size(); ++i) {
                globalToBlockMapping.set(sym.get((int) i).value, (short) i);
                if (sym.size() > 1) {
                    bvSize +=
                            frequencies[(sym.get((int) i).value)]
                                    * codeLength[(sym.get((int) i).value)];
                }
            }

            // Fill in the fixed-size block header.
            blockHeader.blockVectorOffset = (int) superBlockBvSize;
            blockHeader.varSizeHeaderOffset = (int) variableBlockHeaderSize;
            blockHeader.treeHeight = (short) treeHeight;
            blockHeader.sigma = (short) (sigma - 1);

            // Store the superblock mapping.
            for (long i = 0; i < this.alphabetSize; ++i) {
                if (globalToBlockMapping.get((int) i) != this.alphabetSize) {
                    short superBlockChar =
                            globalMapping[(int) (superBlockId * this.alphabetSize + i)];
                    long address =
                            (long) superBlockChar
                                            * (WaveletFixedBlockBoosting.SUPER_BLOCK_SIZE
                                                    / blockSize)
                                    + blockId;
                    short mappingValue =
                            (short)
                                    Math.min(
                                            (short) (this.alphabetSize - 2),
                                            globalToBlockMapping.get((int) i));
                    superBlockHeaderItem.mapping[(int) address] = mappingValue;
                }
            }

            // Update the size of superblock bitvector.
            superBlockBvSize += bvSize;

            // Update the total size of variable-size block headers.
            if (treeHeight > 1) {
                variableBlockHeaderSize += (treeHeight - 1) * 4;
            }
            variableBlockHeaderSize += sigma * 5;
            variableBlockHeaderSize += (sigma - 1) * 2;
        }

        // Allocate the variable-size block header.
        superBlockHeaderItem.varBlockHeadersData = new byte[(int) variableBlockHeaderSize];

        // Fill in variable-size block headers and superblock bitvector.
        long bvRank = 0;
        BitVector superBlockBv = LongArrayBitVector.getInstance().length(superBlockBvSize);
        superBlockBv.fill(false);

        List<Long> blockRank = new ArrayList<>(this.alphabetSize);
        for (int i = 0; i < this.alphabetSize; i++) {
            blockRank.add(0L);
        }

        for (long blockId = 0; blockId < blocksInThisSuperBlock; ++blockId) {
            BlockHeaderItem blockHeader = superBlockHeaderItem.blockHeaders[(int) blockId];
            long blockBeg = blockId * blockSize;
            long blockEnd = Math.min(blockBeg + blockSize, superBlockSize);
            long thisBlockSize = blockEnd - blockBeg;
            int blockPtr = (int) (superBlockPtr + blockBeg);

            // Fill in the variable-size header of the current block
            // and append the block bitvector to superblock bitvector.
            onesCount[0] = 0;
            long superBlockBvOffset = blockHeader.blockVectorOffset;
            long variableBlockHeaderOffset = blockHeader.varSizeHeaderOffset;
            encodeBlock(
                    text,
                    blockPtr,
                    blockRank,
                    thisBlockSize,
                    superBlockBv,
                    onesCount,
                    superBlockBvOffset,
                    superBlockHeaderItem.varBlockHeadersData,
                    variableBlockHeaderOffset);

            blockHeader.blockVectorRank = (int) bvRank;

            // Update block rank.
            bvRank += onesCount[0];
            for (long i = 0; i < thisBlockSize; ++i) {
                int where = text[(int) (blockPtr + i)];
                long what = blockRank.get(where) + 1;
                blockRank.set(where, what);
            }
        }

        // Convert the superblock bitvector to final encoding and store.
        superBlockHeaderItem.rankSupport = new RrrVector(superBlockBv, samplingRateRrr);
    }

    private void assignCanonicalHuffmanCodes(long[] frequency, long[] codeLength, long[] code) {

        Arrays.fill(code, 0L);

        List<Tuple<Long, Short>> sym = new ArrayList<>();
        for (long i = 0; i < this.alphabetSize; ++i) {
            if (frequency[((int) i)] > 0) {
                sym.add(new Tuple<>(codeLength[((int) i)], (short) i));
            }
        }
        sym.sort(LONG_SHORT_TUPLE_COMPARATOR);

        for (long c = 0, i = 0; i < sym.size(); ++i) {
            if (i != 0) {
                c = (c + 1) << (sym.get((int) i).key - sym.get((int) (i - 1)).key);
            }
            code[sym.get((int) i).value] = c;
        }
    }

    private List<Long> removeConsecutives(List<Long> list) {
        List<Long> filter = new ArrayList<>();
        filter.add(list.get(0));
        int pos = 0;
        for (int i = 1; i < list.size(); i++) {
            if (!list.get(i).equals(filter.get(pos))) {
                filter.add(list.get(i));
                ++pos;
            }
        }
        return filter;
    }

    private void encodeBlock(
            short[] text,
            int blockPtr,
            List<Long> blockRank,
            long blockSize,
            BitVector superBlockBv,
            int[] onesCount,
            long superBlockBvOffset,
            byte[] blockHeadersData,
            long blockHeaderPtr) {

        // Compute Huffman code.
        long[] frequencies = new long[this.alphabetSize];
        long[] codeLength = new long[this.alphabetSize];
        long[] code = new long[this.alphabetSize];

        computeSymbolFreq(text, blockPtr, (int) blockSize, frequencies);
        computeHuffmanCodeLengths(frequencies, codeLength);
        assignCanonicalHuffmanCodes(frequencies, codeLength, code);
        long maxCodeLength = -1;
        for (Long currentCodeLength : codeLength) {
            if (currentCodeLength > maxCodeLength) {
                maxCodeLength = currentCodeLength;
            }
        }

        onesCount[0] = 0;
        List<Long> onesInBv = new ArrayList<>(this.alphabetSize);
        for (int i = 0; i < this.alphabetSize; i++) {
            onesInBv.add(0L);
        }

        // Compute bitvectors for all internal nodes
        // of the tree and append to superblock_bv.
        if (listCountEqual(frequencies, 0, frequencies.length, 0L) < (this.alphabetSize - 1)) {

            // Collect IDs of all internal nodes in the tree. The ID of the
            // node is a number whose bits are taken from the root-to-node path
            // (first bit on the path is MSB in ID) prepended with 1, e.g., the
            // node with path 011 has ID 1011 = (DEC)11. This encoding has a
            // number of nice properties: (1) every possible node has unique
            // ID, (2) sorted IDs correspond to nodes in BFS order (and
            // left-to-right within level), which is the order in which we
            // concatenate bitvectors, (3) ID of a sibling with ID x is (x xor 1).
            List<Long> internalNodeIds = new ArrayList<>();
            for (long i = 0; i < this.alphabetSize; ++i) {
                if (frequencies[((int) i)] > 0) {
                    for (long depth = 0; depth < codeLength[((int) i)]; ++depth) {
                        long id =
                                (((1L << codeLength[((int) i)]) | code[((int) i)])
                                        >> (codeLength[((int) i)] - depth));
                        internalNodeIds.add(id);
                    }
                }
            }
            internalNodeIds.sort(Long::compareTo);
            internalNodeIds = removeConsecutives(internalNodeIds);

            // Compute the mapping from internal nodes to bitvectors (which are
            // numbered with consecutive numbers starting from 0, according to
            // the order in which they are concatenated).
            List<Long> internalNodeBvId = new ArrayList<>((int) (1L << maxCodeLength));
            for (int i = 0; i < (1L << maxCodeLength); i++) {
                internalNodeBvId.add(0L);
            }
            for (long i = 0; i < internalNodeIds.size(); ++i) {
                internalNodeBvId.set((internalNodeIds.get((int) i)).intValue(), i);
            }

            // Compute the size of bitvector for every internal node.
            List<Long> internalNodeBvSize = new ArrayList<>(internalNodeIds.size());
            for (int i = 0; i < internalNodeIds.size(); i++) {
                internalNodeBvSize.add(0L);
            }
            for (long i = 0; i < this.alphabetSize; ++i) {
                if (frequencies[((int) i)] > 0) {
                    for (long depth = 0; depth < codeLength[((int) i)]; ++depth) {
                        long id =
                                (((1L << codeLength[((int) i)]) | code[((int) i)])
                                        >> (codeLength[((int) i)] - depth));
                        long position = Math.toIntExact(internalNodeBvId.get((int) id));
                        long previousValue = internalNodeBvSize.get((int) position);
                        internalNodeBvSize.set(
                                (int) position, previousValue + frequencies[((int) i)]);
                    }
                }
            }

            // Allocate bitvectors for all internal nodes.
            List<List<Short>> internalNodeBv = new ArrayList<>();
            for (long i = 0; i < internalNodeIds.size(); ++i) {
                List<Short> list = new ArrayList<>();
                for (int j = 0; j < internalNodeBvSize.get((int) i); j++) {
                    list.add((short) 0);
                }
                internalNodeBv.add(list);
            }

            // Fill in the bitvectors for all internal nodes.
            List<Long> nodeVisitCount = new ArrayList<>((int) (1L << (maxCodeLength + 1)));
            for (int i = 0; i < (int) (1L << (maxCodeLength + 1)); i++) {
                nodeVisitCount.add(0L);
            }

            for (long i = 0; i < blockSize; ++i) {
                short sym = text[(int) (blockPtr + i)];
                long pos = i;
                for (long depth = 0; depth < codeLength[(sym)]; ++depth) {
                    long id =
                            (((1L << codeLength[(sym)]) | code[(sym)])
                                    >>> (codeLength[(sym)] - depth));
                    if (depth > 0) {
                        pos -= nodeVisitCount.get((int) (id ^ 1));
                        nodeVisitCount.set((int) id, nodeVisitCount.get((int) id) + 1);
                    }
                    if ((code[(sym)] & (1L << (codeLength[(sym)] - depth - 1))) != 0) {
                        int internalNodeBvPos = Math.toIntExact(internalNodeBvId.get((int) id));
                        List<Short> aux = internalNodeBv.get(internalNodeBvPos);
                        aux.set((int) pos, (short) 1);

                        long previousValue =
                                onesInBv.get(Math.toIntExact(internalNodeBvId.get((int) id)));
                        onesInBv.set(
                                Math.toIntExact(internalNodeBvId.get((int) id)), previousValue + 1);
                        onesCount[0] += 1;
                    }
                }
                int where = (int) ((1L << codeLength[(sym)]) | code[(sym)]);
                long what = nodeVisitCount.get(where) + 1;
                nodeVisitCount.set(where, what);
            }

            // Append bitvectors of internal nodes to superblock bitvector.
            for (long i = 0; i < internalNodeIds.size(); ++i) {
                for (long j = 0; j < internalNodeBv.get((int) i).size(); ++j) {
                    superBlockBv.set(superBlockBvOffset, internalNodeBv.get((int) i).get((int) j));
                    ++superBlockBvOffset;
                }
            }
        }

        // Fill in the variable-size block header.

        List<Long> codeLengthFrequency = new ArrayList<>((int) maxCodeLength);
        for (int i = 0; i < maxCodeLength; i++) {
            codeLengthFrequency.add(0L);
        }
        for (long i = 0; i < this.alphabetSize; ++i) {
            if (frequencies[((int) i)] > 0 && codeLength[((int) i)] < maxCodeLength) {
                codeLengthFrequency.set(
                        (int) codeLength[((int) i)],
                        codeLengthFrequency.get((int) codeLength[((int) i)]) + 1);
            }
        }

        // level_total_freq[d] = total frequency of symbols that have code
        // length longer than d, i.e., the total length of bitvectors
        // associated with internal nodes at depth d in the tree.
        List<Long> levelTotalFrequency = new ArrayList<>((int) maxCodeLength);
        for (int i = 0; i < maxCodeLength; i++) {
            levelTotalFrequency.add(0L);
        }
        for (long i = 0; i < this.alphabetSize; ++i) {
            if (frequencies[((int) i)] > 0) {
                for (long depth = 1; depth < codeLength[((int) i)]; ++depth) {
                    levelTotalFrequency.set(
                            (int) depth,
                            levelTotalFrequency.get((int) depth) + frequencies[((int) i)]);
                }
            }
        }

        // Store the number of leaves and total size of bitvectors
        // corresponding to internal nodes at each level in the tree
        // (except root level and deepest levels) minus one. Using
        // 2 bytes limits the block size to 2^16.
        int bytePtr = (int) blockHeaderPtr;
        for (long depth = 1; depth < maxCodeLength; ++depth) {
            short what = (short) Math.toIntExact(codeLengthFrequency.get((int) depth));
            short value = (short) (levelTotalFrequency.get((int) depth) - 1);

            byte b0 = (byte) (what & 0xff);
            byte b1 = (byte) ((what >>> 8) & 0xff);
            byte b2 = (byte) (value & 0xff);
            byte b3 = (byte) ((value >>> 8) & 0xff);

            blockHeadersData[bytePtr++] = b0;
            blockHeadersData[bytePtr++] = b1;
            blockHeadersData[bytePtr++] = b2;
            blockHeadersData[bytePtr++] = b3;
        }

        // Sort symbols by frequency.
        List<Tuple<Long, Short>> sym = new ArrayList<>();
        for (long i = 0; i < this.alphabetSize; ++i) {
            if (frequencies[((int) i)] > 0) {
                sym.add(new Tuple<>(codeLength[((int) i)], (short) i));
            }
        }
        sym.sort(LONG_SHORT_TUPLE_COMPARATOR);

        // Store rank value at block boundary (with respect to to superblock
        // boundary) and global symbol for each leaf. Note: using 3 bytes
        // to store rank at block boundary limits the superblock size to 2^24.
        for (long i = 0; i < sym.size(); ++i) {
            short symbol = sym.get((int) i).value;
            long rankValue = blockRank.get(symbol);
            byte b0 = (byte) (symbol & 0xff);
            byte b1 = (byte) ((symbol >>> 8) & 0xff);
            byte b2 = (byte) (rankValue & 0xff);
            byte b3 = (byte) ((rankValue >>> 8) & 0xff);
            byte b4 = (byte) ((rankValue >>> 16) & 0xff);

            blockHeadersData[bytePtr++] = b0;
            blockHeadersData[bytePtr++] = b1;
            blockHeadersData[bytePtr++] = b2;
            blockHeadersData[bytePtr++] = b3;
            blockHeadersData[bytePtr++] = b4;
        }

        // For every internal node, store the number of 1-bits in the
        // bitvector corresponding to that node and all its left siblings
        // (excluding leaves).
        long numberOfInternalNodesCurrentLevel = 1;
        for (long depth = 0, ptr = 0; depth < maxCodeLength; ++depth) {
            long oneBitsCurrentLevelCount = 0;
            for (long j = 0; j < numberOfInternalNodesCurrentLevel; ++j) {
                oneBitsCurrentLevelCount += onesInBv.get((int) ptr++);
                short value = (short) (oneBitsCurrentLevelCount);
                byte b0 = (byte) (value & 0xff);
                byte b1 = (byte) ((value >>> 8) & 0xff);
                blockHeadersData[bytePtr++] = b0;
                blockHeadersData[bytePtr++] = b1;
            }
            if (depth + 1 != maxCodeLength) {
                long nextLevelLeafCount = codeLengthFrequency.get((int) (depth + 1));
                numberOfInternalNodesCurrentLevel <<= 1;
                numberOfInternalNodesCurrentLevel -= nextLevelLeafCount;
            }
        }
    }

    private void encodeSuperBlock(short[] text, long superBlockPtr, long superBlockId) {

        long superBlockBeg = superBlockId * SUPER_BLOCK_SIZE;
        long superBlockEnd = Math.min(superBlockBeg + SUPER_BLOCK_SIZE, size);
        long superBlockSize = superBlockEnd - superBlockBeg;

        // Store ranks at hyperblock boundary.
        long hyperBlockId =
                (superBlockId * WaveletFixedBlockBoosting.SUPER_BLOCK_SIZE) / HYPER_BLOCK_SIZE;
        if (superBlockId * WaveletFixedBlockBoosting.SUPER_BLOCK_SIZE % HYPER_BLOCK_SIZE == 0) {
            for (int i = 0; i < this.alphabetSize; ++i) {
                hyperBlockRank[(int) (hyperBlockId * this.alphabetSize + i)] = count[i];
            }
        }

        // Store ranks at superblock boundary.
        for (int i = 0; i < this.alphabetSize; ++i) {
            superBlockRank[(int) (superBlockId * this.alphabetSize + i)] =
                    (int) (count[i] - hyperBlockRank[(int) (hyperBlockId * this.alphabetSize + i)]);
        }

        // Update symbol counts.
        for (long i = 0; i < superBlockSize; ++i) {
            short symbol = text[(int) (superBlockPtr + i)];
            count[symbol] += 1;
        }

        // Compute superblock sigma and mapping from
        // global alphabet to superblock alphabet.
        long superBlockSigma = 0;
        for (long i = 0; i < this.alphabetSize; ++i) {
            if (superBlockRank[(int) (superBlockId * this.alphabetSize + i)]
                            + hyperBlockRank[(int) (hyperBlockId * this.alphabetSize + i)]
                    != count[(int) i]) {

                globalMapping[(int) (superBlockId * this.alphabetSize + i)] =
                        (short) superBlockSigma++;
            }
        }
        superBlockHeaderItems[(int) superBlockId].sigma = (short) (superBlockSigma - 1);

        // Find the optimal block size. Fast but potentially not optimal version.
        long bestBlockSizeLog = 0;
        long bestEncodingSize = 0;
        long smallestBlockSizeLog = Math.max(0, Math.min(SUPER_BLOCK_SIZE_LOG, 16) - 7);
        long smallestBlockSize = (1L << smallestBlockSizeLog);
        long maxBlocksInSuperBlock = WaveletFixedBlockBoosting.SUPER_BLOCK_SIZE / smallestBlockSize;

        // Allocate auxiliary arrays used to estimate the
        // size of compressed superblock bitvector.
        long[][] frequencies = new long[(int) maxBlocksInSuperBlock][this.alphabetSize];
        long compressedSuperBlockBvSize = 0;
        long prevUncompressedSuperBlockBvSize = 0;

        // Try few different block sizes.
        for (long blockSizeLog = smallestBlockSizeLog;
                blockSizeLog <= Math.min(SUPER_BLOCK_SIZE_LOG, 16);
                ++blockSizeLog) {

            long blockSize = (1L << blockSizeLog);
            long blocksInThisSuperBlock = (superBlockSize + blockSize - 1) / blockSize;

            // Initialize the encoding size with the space for fixed
            // size block headers and the space for superblock mapping.
            long encodingSize =
                    BLOCK_HEADER_ITEM_SIZE * blocksInThisSuperBlock
                            + superBlockSigma
                                    * (WaveletFixedBlockBoosting.SUPER_BLOCK_SIZE / blockSize);

            // Compute symbol frequencies for all blocks.
            if (blockSizeLog == smallestBlockSizeLog) {

                // Compute the values from scratch.
                for (long blockId = 0; blockId < blocksInThisSuperBlock; ++blockId) {
                    long blockBeg = blockId * blockSize;
                    long blockEnd = Math.min(blockBeg + blockSize, superBlockSize);
                    long currentBlockSize = blockEnd - blockBeg;
                    computeSymbolFreq(
                            text,
                            (int) (superBlockPtr + blockBeg),
                            (int) currentBlockSize,
                            frequencies[((int) blockId)]);
                }
            } else {

                // Compute symbol frequency in each block
                // from the frequencies of smaller blocks.
                long prevBlocksInThisSuperBlock =
                        (superBlockSize + (blockSize / 2) - 1) / (blockSize / 2);
                for (long blockId = 0; blockId < prevBlocksInThisSuperBlock; blockId += 2) {
                    for (long c = 0; c < this.alphabetSize; ++c) {

                        long prev = frequencies[((int) blockId)][((int) c)];
                        long sum =
                                (blockId + 1 < prevBlocksInThisSuperBlock
                                        ? (frequencies[((int) (blockId + 1))])[((int) c)]
                                        : 0);
                        frequencies[((int) (blockId >>> 1))][(int) c] = prev + sum;
                    }
                }
            }

            // Add the space for variable size block headers
            // dependent on block alphabet size.
            for (long blockId = 0; blockId < blocksInThisSuperBlock; ++blockId) {
                long count =
                        listCountEqual(
                                frequencies[((int) blockId)],
                                0,
                                frequencies[((int) blockId)].length,
                                0L);
                long blockSigma = this.alphabetSize - count;
                encodingSize += blockSigma * 4;
                encodingSize += (blockSigma - 1) * 2;
            }

            // Compute sizes of block bitvectors for merged blocks.
            long uncompressedSuperBlockBvSize = 0;
            for (long blockId = 0; blockId < blocksInThisSuperBlock; ++blockId) {

                // Compute Huffman codes.
                long[] codeLength = new long[this.alphabetSize];
                computeHuffmanCodeLengths(frequencies[((int) blockId)], codeLength);

                // Add the space for variable size block
                // headers dependent on Huffman tree height.
                long maxCodeLength = -1;
                for (long currentCodeLength : codeLength) {
                    if (currentCodeLength > maxCodeLength) {
                        maxCodeLength = currentCodeLength;
                    }
                }

                if (maxCodeLength > 1) {
                    encodingSize += (maxCodeLength - 1) * 3;
                }

                // Compute size of uncompressed block bitvector.
                for (long c = 0; c < this.alphabetSize; ++c) {
                    uncompressedSuperBlockBvSize +=
                            frequencies[((int) blockId)][((int) c)] * codeLength[((int) c)];
                }
            }

            if (uncompressedSuperBlockBvSize > 0) {

                // We compute the size of headers of compressed superblock
                // bitvector only for the smallest block.
                if (blockSizeLog == smallestBlockSizeLog) {
                    BitVector blockBv =
                            LongArrayBitVector.getInstance().length(uncompressedSuperBlockBvSize);
                    blockBv.fill(false);
                    RrrVector tempRankSupport = new RrrVector(blockBv, samplingRateRrr);
                    compressedSuperBlockBvSize = tempRankSupport.getEstimatedMemoryUsage();
                } else {
                    double scalingFactor =
                            (double) uncompressedSuperBlockBvSize
                                    / (double) prevUncompressedSuperBlockBvSize;
                    compressedSuperBlockBvSize =
                            (long) ((double) compressedSuperBlockBvSize * scalingFactor);
                }

                // Add the estimated size of compressed
                // superblock bitvector to total encoding.
                encodingSize += compressedSuperBlockBvSize;
            }

            prevUncompressedSuperBlockBvSize = uncompressedSuperBlockBvSize;

            // Check if this block size yields better
            // compression than any other tried so far.
            if (blockSizeLog == smallestBlockSizeLog || encodingSize < bestEncodingSize) {
                bestBlockSizeLog = blockSizeLog;
                bestEncodingSize = encodingSize;
            }
        }

        // Encode blocks inside superblock.
        encodeBlocksInSuperblock(text, superBlockPtr, superBlockId, bestBlockSizeLog);
    }

    private int listCountEqual(long[] list, int from, int to, long value) {
        int count = 0;
        for (int i = from; i < to; i++) {
            if (list[(i)] == value) {
                ++count;
            }
        }
        return count;
    }

    /**
     * Computes the rank of symbol {@code symbol} for the range {@code [0, position)}.
     *
     * @param position The exclusive ending position of the range for the rank calculation
     * @param symbol The symbol already mapped to the alphabet
     * @return The number of appearances of symbol {@code symbol} in the given range
     */
    public long rank(long position, short symbol) {

        if (position == 0) {
            return 0;
        }
        if (position > size) {
            position = size;
        }
        if (symbol >= alphabetSize) {
            return 0;
        }

        long hyperBlockId = position / HYPER_BLOCK_SIZE;
        long superBlockId = position / SUPER_BLOCK_SIZE;
        short superBlockC = globalMapping[(int) (superBlockId * this.alphabetSize + symbol)];
        long superBlockIndex = position % SUPER_BLOCK_SIZE;
        SuperBlockHeaderItem superBlockHeader = superBlockHeaderItems[(int) superBlockId];
        long superBlockSigma = (long) superBlockHeader.sigma + 1;
        long blockSizeLog = superBlockHeader.blockSizeLog;
        long blockSize = (1L << blockSizeLog);
        long blocksInSuperBlockLog = (SUPER_BLOCK_SIZE_LOG - blockSizeLog);
        long blockIndex = position & (blockSize - 1);
        long currentBlockSize = Math.min(blockSize, size - (position - blockIndex));
        long blockId = (superBlockIndex >>> blockSizeLog);
        long rankAtSuperBlockBoundary =
                superBlockRank[(int) (superBlockId * this.alphabetSize + symbol)];
        long rankAtHyperBlockBoundary =
                hyperBlockRank[(int) (hyperBlockId * this.alphabetSize + symbol)];

        // special case: c does not occur in the superblock
        if ((superBlockC) >= superBlockSigma) {
            return rankAtHyperBlockBoundary + rankAtSuperBlockBoundary;
        }

        short blockC =
                superBlockHeader
                        .mapping[(int) (((long) superBlockC << blocksInSuperBlockLog) + blockId)];

        if (blockC == this.alphabetSize - 1) { // special case: c does not occur in the block

            // Find the closest block to the right in which c occurs.
            ++blockId;
            long blocksInSuperBlock = (1L << blocksInSuperBlockLog);
            while (blockId < blocksInSuperBlock
                    && superBlockHeader
                                    .mapping[
                                    (int) (((long) superBlockC << blocksInSuperBlockLog) + blockId)]
                            == this.alphabetSize - 1) {
                ++blockId;
            }
            if (blockId == blocksInSuperBlock) {

                // Return the answer from superblock header or global counts.
                if ((superBlockId + 1) * SUPER_BLOCK_SIZE >= size) {
                    return count[symbol];
                } else {
                    return rankAtHyperBlockBoundary
                            + superBlockRank[
                                    (int) ((superBlockId + 1) * this.alphabetSize + symbol)];
                }
            } else {
                blockC =
                        superBlockHeader
                                .mapping[
                                (int) (((long) superBlockC << blocksInSuperBlockLog) + blockId)];

                // Return the rank value from block header.
                BlockHeaderItem blockHeader = superBlockHeader.blockHeaders[(int) blockId];
                long varSizeHeaderOffset = blockHeader.varSizeHeaderOffset;
                long blockTreeHeight = blockHeader.treeHeight;
                int idxForVariableBlockHeaderPtr = (int) varSizeHeaderOffset;
                idxForVariableBlockHeaderPtr += (blockTreeHeight - 1) * 4;
                int variableBlockHeaderPtr32 = idxForVariableBlockHeaderPtr;

                // byte b0 =
                //     superBlockHeader.varBlockHeadersData[(variableBlockHeaderPtr32 + 5 *
                // blockC)];
                // byte b1 =
                //     superBlockHeader
                //         .varBlockHeadersData[(variableBlockHeaderPtr32 + 5 * blockC) + 1];
                // int value = ((b1 << 8) & 0x00ff00 | (b0) & 0x0000ff);
                // assert value == c;
                // if ((value) != c) {
                //     ++block_c;
                // }

                byte b2 =
                        superBlockHeader
                                .varBlockHeadersData[(variableBlockHeaderPtr32 + blockC * 5 + 2)];
                byte b3 =
                        superBlockHeader
                                .varBlockHeadersData[(variableBlockHeaderPtr32 + blockC * 5 + 3)];
                byte b4 =
                        superBlockHeader
                                .varBlockHeadersData[(variableBlockHeaderPtr32 + blockC * 5 + 4)];

                long rankAtBlockBoundary =
                        ((b4 << 16) & 0xff0000 | (b3 << 8) & 0x00ff00 | (b2) & 0x0000ff);
                return rankAtHyperBlockBoundary + rankAtSuperBlockBoundary + rankAtBlockBoundary;
            }
        }

        // Compute rank at block boundary.
        BlockHeaderItem blockHeader = superBlockHeader.blockHeaders[(int) blockId];
        long varSizeHeaderOffset = blockHeader.varSizeHeaderOffset;
        long blockTreeHeight = blockHeader.treeHeight;
        int variableBlockHeaderPtr = (int) varSizeHeaderOffset;
        int variableBlockHeaderPtrTemp = variableBlockHeaderPtr;

        if (blockTreeHeight > 0) {
            variableBlockHeaderPtrTemp += (blockTreeHeight - 1) * 4;
        }

        byte b0 = superBlockHeader.varBlockHeadersData[(variableBlockHeaderPtrTemp + 5 * blockC)];
        byte b1 =
                superBlockHeader.varBlockHeadersData[(variableBlockHeaderPtrTemp + 5 * blockC) + 1];
        int value = ((b1 << 8) & 0x00ff00 | (b0) & 0x0000ff);

        if (value != symbol) {
            ++blockC;
        }

        short b2 =
                superBlockHeader.varBlockHeadersData[(variableBlockHeaderPtrTemp + blockC * 5 + 2)];
        short b3 =
                superBlockHeader.varBlockHeadersData[(variableBlockHeaderPtrTemp + blockC * 5 + 3)];
        short b4 =
                superBlockHeader.varBlockHeadersData[(variableBlockHeaderPtrTemp + blockC * 5 + 4)];
        long rankAtBlockBoundary = ((b4 << 16) & 0xff0000 | (b3 << 8) & 0x00ff00 | (b2) & 0x0000ff);

        // Answer rank query inside block.
        if (blockTreeHeight == 0) { // special case: block was a run of single symbol
            return rankAtHyperBlockBoundary
                    + rankAtSuperBlockBoundary
                    + rankAtBlockBoundary
                    + blockIndex;
        }

        long codeResult =
                restoreCodeFromBlockHeader(
                        blockC,
                        superBlockHeader.varBlockHeadersData,
                        (int) varSizeHeaderOffset,
                        blockTreeHeight);

        int code = (int) (codeResult >>> 32);
        int codeLength = (int) (codeResult);

        long bvRank =
                blockHeader.blockVectorRank; // rank (in superblock bv) at the beginning of current
        // level
        long bvOffset =
                blockHeader.blockVectorOffset; // starting pos (in superblock bv) of bv at current
        // level
        long internalNodesCount = 1; // number of internal nodes at current level
        long leftSiblingsCount = 0; // number of left siblings (excluding leaves) of current node
        long leftSiblingsTotalBvSize = 0; // total size of bv's corresponding to left siblings of
        // current node (excluding leaves)
        long currentNodeBvSize = currentBlockSize; // size of bitvector of current node
        long currentDepthTotalBvSize =
                currentNodeBvSize; // total size of bitvectors at current depth
        long currentNodeRank = blockIndex; // the rank value refined at each level

        // We maintain second pointer to variable-size block header. It is
        // used to extract total number of 1s in bitvectors corresponding to
        // left siblings of current node (excluding leaves) and also including
        // 1s in the bitvector of current node.
        long blockSigma = (long) blockHeader.sigma + 1;

        variableBlockHeaderPtrTemp = variableBlockHeaderPtr;
        variableBlockHeaderPtrTemp += (blockTreeHeight - 1) * 4;
        variableBlockHeaderPtrTemp += blockSigma * 5;
        int secondVariableBlockHeaderPtr = variableBlockHeaderPtrTemp;

        // Traverse the tree.
        for (long depth = 0; depth < codeLength; ++depth) {

            // Compute the number of 1s in current node.
            long rank1 =
                    superBlockHeader.rankSupport.rankOnes(
                            (int) (bvOffset + leftSiblingsTotalBvSize + currentNodeRank));
            long leftSiblingsTotalOnesCount = 0;

            if (leftSiblingsCount > 0) {
                b0 =
                        superBlockHeader
                                .varBlockHeadersData[
                                ((int)
                                        (secondVariableBlockHeaderPtr
                                                + 2 * (leftSiblingsCount - 1)))];
                b1 =
                        superBlockHeader
                                .varBlockHeadersData[
                                ((int) (secondVariableBlockHeaderPtr + 2 * (leftSiblingsCount - 1))
                                        + 1)];
                leftSiblingsTotalOnesCount = ((b1 << 8) & 0x00ff00 | (b0) & 0x0000ff);
            }
            rank1 -= bvRank + leftSiblingsTotalOnesCount;

            // Compute remaining stats about current node.
            int where = (int) (secondVariableBlockHeaderPtr + 2 * leftSiblingsCount);
            b0 = superBlockHeader.varBlockHeadersData[where];
            b1 = superBlockHeader.varBlockHeadersData[where + 1];
            long currentNodeOneCount =
                    ((b1 << 8) & 0x00ff00 | (b0) & 0x0000ff) - leftSiblingsTotalOnesCount;

            long currentNodeZeroCount = currentNodeBvSize - currentNodeOneCount;
            long rank0 = currentNodeRank - rank1;

            // Update navigational info.
            b0 =
                    superBlockHeader
                            .varBlockHeadersData[
                            ((int) (secondVariableBlockHeaderPtr + 2 * (internalNodesCount - 1)))];
            b1 =
                    superBlockHeader
                            .varBlockHeadersData[
                            ((int) (secondVariableBlockHeaderPtr + 2 * (internalNodesCount - 1))
                                    + 1)];
            bvRank += ((b1 << 8) & 0x00ff00 | (b0) & 0x0000ff);

            secondVariableBlockHeaderPtr += 2 * internalNodesCount;
            leftSiblingsCount <<= 1;

            // Update rank.
            long nextBit = (code & (1L << (codeLength - depth - 1)));
            if (nextBit != 0) {
                currentNodeRank = rank1;
                currentNodeBvSize = currentNodeOneCount;
                ++leftSiblingsCount;
                leftSiblingsTotalBvSize += currentNodeZeroCount;
            } else {
                currentNodeRank = rank0;
                currentNodeBvSize = currentNodeZeroCount;
            }

            // Update navigational info.
            if (depth + 1 != codeLength) {

                // Decode leaf count and total size of bitvectors corresponding
                // to internal nodes on the next level of the tree from the
                // variable-size block header.
                b0 = superBlockHeader.varBlockHeadersData[variableBlockHeaderPtr];
                b1 = superBlockHeader.varBlockHeadersData[variableBlockHeaderPtr + 1];
                long nextLevelLeafCount = ((b1 << 8) & 0x00ff00 | (b0) & 0x0000ff);
                variableBlockHeaderPtr += 2;
                // Note: the dereferencing of the pointer takes a uint16_t which is two bytes.
                // In our encoding, thats two shorts that we need to put together
                b0 = superBlockHeader.varBlockHeadersData[variableBlockHeaderPtr];
                b1 = superBlockHeader.varBlockHeadersData[variableBlockHeaderPtr + 1];
                long nextLevelTotalBvSize = ((b1 << 8) & 0x00ff00 | (b0) & 0x0000ff) + 1;
                variableBlockHeaderPtr += 2;

                // Update the total size of bitvectors
                // corresponding to left siblings of current node.
                leftSiblingsTotalBvSize -= (currentDepthTotalBvSize - nextLevelTotalBvSize);

                // Update bitvector offset and total
                // size of bitvectors at current depth.
                bvOffset += currentDepthTotalBvSize;
                currentDepthTotalBvSize = nextLevelTotalBvSize;

                // Update the number of internal nodes at current level.
                internalNodesCount <<= 1;
                internalNodesCount -= nextLevelLeafCount;

                // Update the number of left siblings of current node.
                leftSiblingsCount -= nextLevelLeafCount;
            }
        }

        return rankAtHyperBlockBoundary
                + rankAtSuperBlockBoundary
                + rankAtBlockBoundary
                + currentNodeRank;
    }

    /**
     * Computes the rank of symbol {@code symbol} for the range {@code [0, position)}.
     *
     * @param position The exclusive ending position of the range for the rank calculation
     * @param symbol The symbol
     * @return The number of appearances of symbol {@code symbol} in the given range
     */
    public long rank(long position, char symbol) {
        return rank(position, (short) symbol);
    }

    /**
     * Returns the symbol and its occurrence (until such point) at position {@code position}.
     *
     * @param position The position for which we want to know the symbol
     * @return A long containing two ints such that the first 32 bits is the occurrence and the
     *     second value is the symbol code
     */
    public long inverseSelect(long position) {

        long hyperBlockId = position / HYPER_BLOCK_SIZE;
        long superBlockId = position / SUPER_BLOCK_SIZE;
        long superBlockIndex = position % SUPER_BLOCK_SIZE;
        SuperBlockHeaderItem superBlockHeader = superBlockHeaderItems[(int) superBlockId];

        long blockSizeLog = superBlockHeader.blockSizeLog;

        long blockSize = (1L << blockSizeLog);
        long blockIndex = position & (blockSize - 1);
        long currentBlockSize = Math.min(blockSize, size - (position - blockIndex));
        long blockId = (superBlockIndex >> blockSizeLog);
        BlockHeaderItem blockHeader = superBlockHeader.blockHeaders[(int) blockId];
        long varSizeHeaderOffset = blockHeader.varSizeHeaderOffset;
        long blockTreeHeight = blockHeader.treeHeight;
        int variableBlockHeaderPtr8 = (int) varSizeHeaderOffset;
        int copyVariableBlockHeaderPtr8 = variableBlockHeaderPtr8;
        int variableBlockHeaderPtrTemp8 = variableBlockHeaderPtr8;
        if (blockTreeHeight > 0) {
            variableBlockHeaderPtrTemp8 += (blockTreeHeight - 1) * 4;
        }
        int variableBlockHeaderPtr32 = variableBlockHeaderPtrTemp8;

        if (blockTreeHeight == 0) {
            byte b0 = superBlockHeader.varBlockHeadersData[variableBlockHeaderPtr32];
            byte b1 = superBlockHeader.varBlockHeadersData[variableBlockHeaderPtr32 + 1];
            int c = ((((short) b1 << 8) & 0x00ff00) | ((short) b0)) & 0x00ff;

            if (position == 0) {
                return c;
            } else {
                byte b2 = superBlockHeader.varBlockHeadersData[variableBlockHeaderPtr32 + 2];
                byte b3 = superBlockHeader.varBlockHeadersData[variableBlockHeaderPtr32 + 3];
                byte b4 = superBlockHeader.varBlockHeadersData[variableBlockHeaderPtr32 + 4];
                long rankAtBlockBoundary =
                        ((b4 << 16) & 0xff0000 | (b3 << 8) & 0x00ff00 | (b2) & 0x0000ff);
                long rankAtSuperBlockBoundary =
                        superBlockRank[(int) (superBlockId * this.alphabetSize + c)];
                long rankAtHyperBlockBoundary =
                        hyperBlockRank[(int) (hyperBlockId * this.alphabetSize + c)];

                long resultRank =
                        rankAtHyperBlockBoundary
                                + rankAtSuperBlockBoundary
                                + rankAtBlockBoundary
                                + blockIndex;

                return (resultRank << 32) | c;
            }
        }

        long code = 0;
        long codeLength = 0;

        long bvRank =
                blockHeader.blockVectorRank; // rank (in superblock bv) at the beginning of current
        // level
        long bvOffset =
                blockHeader.blockVectorOffset; // starting pos (in superblock bv) of bv at current
        // level
        long internalNodesCount = 1; // number of internal nodes at current level
        long leftSiblingsCount = 0; // number of left siblings (excluding leaves) of current node
        long leftSiblingsTotalBvSize = 0; // total size of bv's corresponding to left siblings
        // of current node (excluding leaves)
        long currentNodeBvSize = currentBlockSize; // size of bitvector of current node
        long currentDepthTotalBvSize =
                currentNodeBvSize; // total size of bitvectors at current depth
        long currentNodeRank = blockIndex; // the rank value refined at each level

        // We maintain second pointer to variable-size block header. It is
        // used to extract total number of 1s in bitvectors corresponding to
        // left siblings of current node (excluding leaves) and also including
        // 1s in the bitvector of current node.
        long blockSigma = (long) blockHeader.sigma + 1;
        variableBlockHeaderPtrTemp8 = variableBlockHeaderPtr8;
        variableBlockHeaderPtrTemp8 += (blockTreeHeight - 1) * 4;
        variableBlockHeaderPtrTemp8 += blockSigma * 5; // now its 5 bytes increase
        int secondVariableBlockHeaderPtr16 = variableBlockHeaderPtrTemp8;

        // Traverse the tree.
        for (long depth = 0; ; ++depth) {

            // Compute the number of 1s in current node.
            int rankPosition = (int) (bvOffset + leftSiblingsTotalBvSize + currentNodeRank);
            long rank1 = superBlockHeader.rankSupport.rankOnes(rankPosition);
            boolean nextBit =
                    superBlockHeader.rankSupport.access(
                            (int) (bvOffset + leftSiblingsTotalBvSize + currentNodeRank));
            long leftSiblingsTotalOnesCount = 0;
            if (leftSiblingsCount > 0) {
                byte b0 =
                        superBlockHeader
                                .varBlockHeadersData[
                                (int)
                                        (secondVariableBlockHeaderPtr16
                                                + 2 * (leftSiblingsCount - 1))];
                byte b1 =
                        superBlockHeader
                                .varBlockHeadersData[
                                (int) (secondVariableBlockHeaderPtr16 + 2 * (leftSiblingsCount - 1))
                                        + 1];
                leftSiblingsTotalOnesCount = ((b1 << 8) & 0x00ff00 | (b0) & 0x0000ff);
            }
            rank1 -= bvRank + leftSiblingsTotalOnesCount;

            // Compute remaining stats about current node.
            byte b0 =
                    superBlockHeader
                            .varBlockHeadersData[
                            (int) (secondVariableBlockHeaderPtr16 + 2 * (leftSiblingsCount))];
            byte b1 =
                    superBlockHeader
                            .varBlockHeadersData[
                            (int) (secondVariableBlockHeaderPtr16 + 2 * (leftSiblingsCount)) + 1];

            long curNodeOneCount =
                    ((b1 << 8) & 0x00ff00 | (b0) & 0x0000ff) - leftSiblingsTotalOnesCount;

            long curNodeZeroCount = currentNodeBvSize - curNodeOneCount;
            long rank0 = currentNodeRank - rank1;

            // Update navigational info.
            b0 =
                    superBlockHeader
                            .varBlockHeadersData[
                            (int) (secondVariableBlockHeaderPtr16 + 2 * (internalNodesCount - 1))];
            b1 =
                    superBlockHeader
                            .varBlockHeadersData[
                            (int) (secondVariableBlockHeaderPtr16 + 2 * (internalNodesCount - 1))
                                    + 1];
            bvRank += ((b1 << 8) & 0x00ff00 | (b0) & 0x0000ff);
            secondVariableBlockHeaderPtr16 += internalNodesCount * 2;
            leftSiblingsCount <<= 1;

            // Update rank.
            code <<= 1;
            ++codeLength;
            if (nextBit) {
                code |= 1;
                currentNodeRank = rank1;
                currentNodeBvSize = curNodeOneCount;
                ++leftSiblingsCount;
                leftSiblingsTotalBvSize += curNodeZeroCount;
            } else {
                currentNodeRank = rank0;
                currentNodeBvSize = curNodeZeroCount;
            }

            // Update navigational info.
            if (depth + 1 < blockTreeHeight) {

                // Decode leaf count and total size of bitvectors corresponding
                // to internal nodes on the next level of the tree from the
                // variable-size block header.
                b0 = superBlockHeader.varBlockHeadersData[variableBlockHeaderPtr8];
                b1 = superBlockHeader.varBlockHeadersData[variableBlockHeaderPtr8 + 1];
                long nextLevelLeafCount = ((b1 << 8) & 0x00ff00 | (b0) & 0x0000ff);

                variableBlockHeaderPtr8 += 2;
                b0 = superBlockHeader.varBlockHeadersData[variableBlockHeaderPtr8];
                b1 = superBlockHeader.varBlockHeadersData[variableBlockHeaderPtr8 + 1];
                long nextLevelTotalBvSize = ((b1 << 8) & 0x00ff00 | (b0) & 0x0000ff) + 1;
                variableBlockHeaderPtr8 += 2;

                // Update the total size of bitvectors
                // corresponding to left siblings of current node.
                leftSiblingsTotalBvSize -= (currentDepthTotalBvSize - nextLevelTotalBvSize);

                // Update bitvector offset and total
                // size of bitvectors at current depth.
                bvOffset += currentDepthTotalBvSize;
                currentDepthTotalBvSize = nextLevelTotalBvSize;

                // Update the number of internal nodes at current level.
                internalNodesCount <<= 1;
                internalNodesCount -= nextLevelLeafCount;

                // Update the number of left siblings of current node of exit.
                if (leftSiblingsCount >= nextLevelLeafCount) {
                    leftSiblingsCount -= nextLevelLeafCount;
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        long blockC =
                computeSymbolFromBlockHeader(
                        superBlockHeader.varBlockHeadersData,
                        copyVariableBlockHeaderPtr8,
                        code,
                        codeLength);
        byte b0 =
                superBlockHeader.varBlockHeadersData[(int) (variableBlockHeaderPtr32 + 5 * blockC)];
        byte b1 =
                superBlockHeader
                        .varBlockHeadersData[(int) (variableBlockHeaderPtr32 + 5 * blockC) + 1];
        int c = ((b1 << 8) & 0x00ff00 | (b0) & 0x0000ff);

        if (position == 0) {
            return c;
        } else {

            byte b2 =
                    superBlockHeader
                            .varBlockHeadersData[(int) (variableBlockHeaderPtr32 + blockC * 5 + 2)];
            byte b3 =
                    superBlockHeader
                            .varBlockHeadersData[(int) (variableBlockHeaderPtr32 + blockC * 5 + 3)];
            byte b4 =
                    superBlockHeader
                            .varBlockHeadersData[(int) (variableBlockHeaderPtr32 + blockC * 5 + 4)];
            long rankAtBlockBoundary =
                    ((b4 << 16) & 0xff0000 | (b3 << 8) & 0x00ff00 | (b2) & 0x0000ff);

            long rankAtSuperBlockBoundary =
                    superBlockRank[(int) (superBlockId * this.alphabetSize + c)];
            long rankAtHyperBlockBoundary =
                    hyperBlockRank[(int) (hyperBlockId * this.alphabetSize + c)];

            long resultRank =
                    rankAtHyperBlockBoundary
                            + rankAtSuperBlockBoundary
                            + rankAtBlockBoundary
                            + currentNodeRank;

            return (resultRank << 32) | c;
        }
    }

    /**
     * Serializes this object to an {@code ObjectOutput} stream.
     *
     * @param objectOutput The stream to which the object will be written
     */
    public void write(ObjectOutput objectOutput) throws IOException {
        objectOutput.writeByte(SERIAL_VERSION_V0);
        objectOutput.writeLong(size);
        objectOutput.writeInt(alphabetSize);
        objectOutput.writeInt(samplingRateRrr);

        objectOutput.writeInt(count.length);
        for (long v : count) {
            objectOutput.writeLong(v);
        }
        objectOutput.writeInt(hyperBlockRank.length);
        for (long v : hyperBlockRank) {
            objectOutput.writeLong(v);
        }
        objectOutput.writeInt(superBlockRank.length);
        for (int v : superBlockRank) {
            objectOutput.writeInt(v);
        }
        objectOutput.writeInt(globalMapping.length);
        for (short v : globalMapping) {
            objectOutput.writeShort(v);
        }
        objectOutput.writeInt(superBlockHeaderItems.length);
        for (SuperBlockHeaderItem sbhi : superBlockHeaderItems) {
            sbhi.write(objectOutput);
        }
    }

    @Override
    public int hashCode() {
        int hash = 0;
        for (SuperBlockHeaderItem s : superBlockHeaderItems) {
            hash += s.hashCode();
        }
        return (int)
                (size
                        + alphabetSize
                        + samplingRateRrr
                        + Arrays.hashCode(count)
                        + Arrays.hashCode(hyperBlockRank)
                        + Arrays.hashCode(superBlockRank)
                        + Arrays.hashCode(globalMapping)
                        + hash);
    }

    private static final class BlockHeaderItem {

        public int blockVectorRank; // rank at the beginning of block vector
        public int blockVectorOffset; // offset in the superblock bitvector
        public int varSizeHeaderOffset; // offset in the array storing variable-size block header
        public short sigma; // block alphabet size - 1
        public short treeHeight; // height of the wavelet tree for block

        public static BlockHeaderItem read(ObjectInput objectInput) throws IOException {
            BlockHeaderItem bhi = new BlockHeaderItem();
            bhi.blockVectorRank = objectInput.readInt();
            bhi.blockVectorOffset = objectInput.readInt();
            bhi.varSizeHeaderOffset = objectInput.readInt();
            bhi.sigma = objectInput.readShort();
            bhi.treeHeight = objectInput.readShort();
            return bhi;
        }

        public void write(ObjectOutput objectOutput) throws IOException {
            objectOutput.writeInt(blockVectorRank);
            objectOutput.writeInt(blockVectorOffset);
            objectOutput.writeInt(varSizeHeaderOffset);
            objectOutput.writeShort(sigma);
            objectOutput.writeShort(treeHeight);
        }

        @Override
        public int hashCode() {
            return blockVectorRank + blockVectorOffset + varSizeHeaderOffset + sigma + treeHeight;
        }
    }

    private static final class SuperBlockHeaderItem {

        public short sigma;
        public short blockSizeLog;
        public RrrVector rankSupport;
        public BlockHeaderItem[] blockHeaders; // fixed-size block header
        public byte[] varBlockHeadersData;
        public short[] mapping; // mapping from superblock alphabet to block alphabet

        public static SuperBlockHeaderItem read(ObjectInput objectInput) throws IOException {
            SuperBlockHeaderItem sbhi = new SuperBlockHeaderItem();
            sbhi.sigma = objectInput.readShort();
            sbhi.blockSizeLog = objectInput.readShort();
            sbhi.rankSupport = RrrVector.read(objectInput);
            BlockHeaderItem[] bhi = new BlockHeaderItem[objectInput.readInt()];
            for (int i = 0; i < bhi.length; i++) {
                bhi[i] = BlockHeaderItem.read(objectInput);
            }
            sbhi.blockHeaders = bhi;
            byte[] varBlockHeadersData = new byte[objectInput.readInt()];
            objectInput.readFully(varBlockHeadersData, 0, varBlockHeadersData.length);
            sbhi.varBlockHeadersData = varBlockHeadersData;
            short[] mapping = new short[objectInput.readInt()];
            for (int i = 0; i < mapping.length; i++) {
                mapping[i] = objectInput.readShort();
            }
            sbhi.mapping = mapping;
            return sbhi;
        }

        public void write(ObjectOutput objectOutput) throws IOException {
            objectOutput.writeShort(sigma);
            objectOutput.writeShort(blockSizeLog);
            rankSupport.write(objectOutput);
            objectOutput.writeInt(blockHeaders.length);
            for (BlockHeaderItem bhi : blockHeaders) {
                bhi.write(objectOutput);
            }
            objectOutput.writeInt(varBlockHeadersData.length);
            for (byte b : varBlockHeadersData) {
                objectOutput.writeByte(b);
            }
            objectOutput.writeInt(mapping.length);
            for (short s : mapping) {
                objectOutput.writeShort(s);
            }
        }

        @Override
        public int hashCode() {
            int hash = 0;
            for (BlockHeaderItem b : blockHeaders) {
                hash += b.hashCode();
            }
            return sigma
                    + blockSizeLog
                    + rankSupport.hashCode()
                    + Arrays.hashCode(varBlockHeadersData)
                    + Arrays.hashCode(mapping)
                    + hash;
        }
    }

    private static Comparator<Tuple<Long, List<Short>>>
            getInvertedPriorityQueueComparatorForHuffman() {
        return new Comparator<Tuple<Long, List<Short>>>() {

            @Override
            public int compare(Tuple<Long, List<Short>> o1, Tuple<Long, List<Short>> o2) {
                if (o1.key < o2.key) {
                    return -1;
                } else if (o1.key > o2.key) {
                    return 1;
                } else {
                    // compare lists element wise
                    for (int i = 0; i < Math.min(o1.value.size(), o2.value.size()); i++) {
                        if (o1.value.get(i) < o2.value.get(i)) {
                            return -1;
                        } else if (o1.value.get(i) > o2.value.get(i)) {
                            return 1;
                        }
                    }
                }
                return 0;
            }
        };
    }

    private static Comparator<Tuple<Long, Short>> getLongShortTupleComparator() {
        return (o1, o2) -> {
            int result = o1.key.compareTo(o2.key);
            if (result == 0) {
                return o1.value.compareTo(o2.value);
            } else {
                return result;
            }
        };
    }
}
