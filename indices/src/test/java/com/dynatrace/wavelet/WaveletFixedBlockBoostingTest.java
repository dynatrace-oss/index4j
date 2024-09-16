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
package com.dynatrace.wavelet;

import static com.dynatrace.util.Util.HDFS_2k_CHAR;
import static com.dynatrace.util.Util.LONGER_TEXT;
import static com.dynatrace.util.Util.SMALLER_TEXT;
import static com.dynatrace.util.Util.convertAlphabetToMonotonicSequence;
import static com.dynatrace.util.Util.countPreviousOccurrencesOfSymbol;
import static com.dynatrace.util.Util.getReverseMonotonicAlphabet;
import static com.dynatrace.util.Util.transformStringIntoMonotonicWithAlphabet;
import static com.dynatrace.wavelet.WaveletFixedBlockBoosting.INVERTED_PRIORITY_QUEUE_COMPARATOR_FOR_HUFFMAN;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dynatrace.serialization.Serialization;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

public class WaveletFixedBlockBoostingTest {

    @Test
    void testEmptyWavelet() {
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new WaveletFixedBlockBoosting("".toCharArray()));
        assertThat(exception.getMessage()).isEqualTo("Input length must be > 0");
    }

    @Test
    void testSingleSymbolWavelet() {
        WaveletFixedBlockBoosting wavelet = new WaveletFixedBlockBoosting("a".toCharArray());
        assertThat(wavelet.rank(1, 'a')).isEqualTo(1);
        assertThat(wavelet.rank(1, 'b')).isEqualTo(0);
    }

    @Test
    void testWaveletRankFromSmallText() {
        WaveletFixedBlockBoosting wavelet = new WaveletFixedBlockBoosting(SMALLER_TEXT);
        long rank = wavelet.rank(6, 'a');
        assertThat(rank).isEqualTo(2);
        rank = wavelet.rank(SMALLER_TEXT.length, 'a');
        assertThat(rank).isEqualTo(4);
        rank = wavelet.rank(SMALLER_TEXT.length, 'h');
        assertThat(rank).isEqualTo(4);
        rank = wavelet.rank(19, 'i');
        assertThat(rank).isEqualTo(1);
    }

    @Test
    void testWaveletAccessFromLargeText() {
        WaveletFixedBlockBoosting wavelet = new WaveletFixedBlockBoosting(LONGER_TEXT);
        for (int i = 0; i < LONGER_TEXT.length; i++) {
            long ithAndSymbol = wavelet.inverseSelect(i);
            assertThat((char) (ithAndSymbol)).isEqualTo(LONGER_TEXT[i]);
        }
    }

    @Test
    void testRankQueryOfNonExistingCharacters() {
        WaveletFixedBlockBoosting wavelet = new WaveletFixedBlockBoosting(SMALLER_TEXT);
        long rank = wavelet.rank(22, 'Z');
        assertThat(rank).isEqualTo(0);
    }

    @Test
    void testCornerCaseWhereAllValuesAreOne() {
        short[] string = new short[100];
        Arrays.fill(string, (short) 1);
        WaveletFixedBlockBoosting wavelet = new WaveletFixedBlockBoosting(string);

        int retrieved = (int) (wavelet.inverseSelect(0));
        assertThat(retrieved).isEqualTo(1);

        retrieved = (int) wavelet.inverseSelect(5);
        assertThat(retrieved).isEqualTo(1);
    }

    @Test
    void testCornerCaseWhereRankingOutOfBounds() {
        short[] string = new short[30_000];
        Arrays.fill(string, (short) 3);
        string[28_000] = 2;
        WaveletFixedBlockBoosting wavelet = new WaveletFixedBlockBoosting(string);
        long count = wavelet.rank(90_000, (short) 2);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void testCornerCaseWhereRankingOutOfBoundsLargeBlocks() {
        char[] string = new char[3_000_000];
        Arrays.fill(string, 'b');
        string[2_800_000] = 'a';
        Map<Character, Short> alphabetMap = convertAlphabetToMonotonicSequence(string);
        short[] monotonicSequence = transformStringIntoMonotonicWithAlphabet(string, alphabetMap);
        WaveletFixedBlockBoosting wavelet = new WaveletFixedBlockBoosting(monotonicSequence);
        long count = wavelet.rank(6_900_000, alphabetMap.get('a'));
        assertThat(count).isEqualTo(1);
    }

    @Test
    void testCornerCaseWhereRankIsInFirstSuperBlockButSmallerThanString() {
        char[] string = new char[3_000_000];
        Arrays.fill(string, 'b');
        string[100] = 'a';
        Map<Character, Short> alphabetMap = convertAlphabetToMonotonicSequence(string);
        short[] monotonicSequence = transformStringIntoMonotonicWithAlphabet(string, alphabetMap);
        WaveletFixedBlockBoosting wavelet = new WaveletFixedBlockBoosting(monotonicSequence);
        long count = wavelet.rank(1_000_000, alphabetMap.get('a'));
        assertThat(count).isEqualTo(1);
    }

    @Test
    void testWaveletRankFromLongText() {
        WaveletFixedBlockBoosting wavelet = new WaveletFixedBlockBoosting(LONGER_TEXT);
        Random r = new Random(42);
        for (int i = 0; i < 1_000; i++) {
            int range = r.nextInt(LONGER_TEXT.length);
            char symbol = LONGER_TEXT[r.nextInt(LONGER_TEXT.length)];
            long rank = wavelet.rank(range, symbol);
            int actualRank = countPreviousOccurrencesOfSymbol(symbol, LONGER_TEXT, range);
            assertThat(rank).isEqualTo(actualRank);
        }
    }

    @Test
    void testWaveletRankFromLogsWithMapping() {
        // Note: alphabet is mapped here because there are characters encoded with a value higher
        // than
        // 32,767
        Map<Character, Short> alphabetMap = convertAlphabetToMonotonicSequence(HDFS_2k_CHAR);
        short[] monotonicSequence =
                transformStringIntoMonotonicWithAlphabet(HDFS_2k_CHAR, alphabetMap);
        WaveletFixedBlockBoosting wavelet = new WaveletFixedBlockBoosting(monotonicSequence);
        Random r = new Random(42);
        for (int i = 0; i < 1_000; i++) {
            int range = r.nextInt(monotonicSequence.length);
            int posOfRandomSymbol = r.nextInt(monotonicSequence.length);
            short mappedSymbol = monotonicSequence[posOfRandomSymbol];
            char symbol = HDFS_2k_CHAR[posOfRandomSymbol];
            long rank = wavelet.rank(range, mappedSymbol);
            int actualRank = countPreviousOccurrencesOfSymbol(symbol, HDFS_2k_CHAR, range);
            assertThat(rank).isEqualTo(actualRank);
        }
    }

    @Test
    void testWaveletRankFromUtf8() {
        char[] text =
                "Chodzą jeże koło wieży, 操據支救数料新方旅日旦时映時智更最月有服未本材来東 spotkał je tam pewien Jerzyk."
                        .toCharArray();

        // Note: alphabet is mapped here because there are characters encoded with a value higher
        // than
        // 32,767
        Map<Character, Short> alphabetMap = convertAlphabetToMonotonicSequence(text);
        short[] monotonicSequence = transformStringIntoMonotonicWithAlphabet(text, alphabetMap);

        WaveletFixedBlockBoosting wavelet = new WaveletFixedBlockBoosting(monotonicSequence);

        long rank = wavelet.rank(36, alphabetMap.getOrDefault('ł', Short.MAX_VALUE));
        int actualRank = countPreviousOccurrencesOfSymbol('ł', text, 36);
        assertThat(rank).isEqualTo(actualRank);

        rank = wavelet.rank(68, alphabetMap.getOrDefault('最', Short.MAX_VALUE));
        actualRank = countPreviousOccurrencesOfSymbol('最', text, 68);
        assertThat(rank).isEqualTo(actualRank);

        rank = wavelet.rank(12, alphabetMap.getOrDefault('人', Short.MAX_VALUE));
        actualRank = countPreviousOccurrencesOfSymbol('人', text, 12);
        assertThat(rank).isEqualTo(actualRank);
    }

    @Test
    void testLargeAlphabet() {
        char[] text =
                ("!\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefgh"
                                + "ijklmnopqrstuvwxyz{|}~ペホボポマムメャュョラリルレロワン・ー一上下不与世中主之乐了事云互亚享京人介他们件份企休会位住"
                                + "作使便保信修假停健價優儲光免全公六关其ดตถทธนบปผพฟภมยรลวศสหอะัาิีืุูเแโไ็่้์ạấầậắẳặếềểệỏộ"
                                + "ớờở預页领风首香駕马高點동비서스용의이‑–—‘’•…‹›‼⁈⁉€™⇒►◄✓➡、。【】〰ねのはへまめもよらりるをんァアィイウェエオカキクグ"
                                + "サザシジスズタダチッツテデトドナニネノハバパビピフブプベペホボポマムメャュョラリルレロワン・ー一上下不与世中主之乐了事云互亚享京人介他们件"
                                + "份企休会位住作使便保信修假停健價優儲光免全公六关其再冒决凡処出击切划列创初利到券前劃力功务动動務化北區半华协卓危历原参及友取古可台史号司吉"
                                + "名向员和咨品員商問喜四回国圖在地场型城報壽备外多大天奢女好姓婦子字存季安官定宝客家宿密實寶対專尋小少尔尚屈展工市年应店度庫康廠建式引弘当後"
                                + "心怀急性总您情惠意感我户所技投持指捷探援搜擇操據支救数料新方旅日旦时映時智更最月有服未本材来東松林查株格検業楽概標欢款比氏求江決汽注派测海"
                                + "淘済港澳灣点無版特现珠球理生用由电画留當疾療発百的盛目看碩示社福私科秘程站第管精系約級索紧紹絡給統網线经络统网置美義耀者而联聚聯職股肯能腕"
                                + "臣自航花荣获華蓄行裝西要見覚觀观览解計託証試詳認證计询象貓販責買資質購购赏起趣路車較載轎车轻载迎近适选通遊道選配酒醫鉴錄錶铃银销长閉開間בت")
                        .toCharArray();

        // Note: alphabet is mapped here because there are characters encoded with a value higher
        // than
        // 32,767
        Map<Character, Short> alphabetMap = convertAlphabetToMonotonicSequence(text);
        Map<Short, Character> reverseMap = getReverseMonotonicAlphabet(text);
        short[] monotonicSequence = transformStringIntoMonotonicWithAlphabet(text, alphabetMap);

        WaveletFixedBlockBoosting wavelet = new WaveletFixedBlockBoosting(monotonicSequence);

        Random r = new Random(8828280);
        for (int i = 0; i < 1_000; i++) {
            // Get a random position (#1), and a random symbol at another random position (#2)
            int pos = r.nextInt(text.length);
            int symbolPos = r.nextInt(text.length);
            short mappedSymbol = monotonicSequence[symbolPos];
            char symbol = text[symbolPos];

            // Assert that the random symbol until random position (#1) is the same
            long rank = wavelet.rank(pos, mappedSymbol);
            int actualRank = countPreviousOccurrencesOfSymbol(symbol, text, pos);
            assertThat(rank).isEqualTo(actualRank);

            // Assert that accessing the random position (#2) yields the symbol
            long ithAndSymbol = wavelet.inverseSelect(symbolPos);
            assertThat(reverseMap.get((short) ithAndSymbol)).isEqualTo(symbol);
        }
    }

    @Test
    void testSerializationWithLogQueries() throws IOException {

        // Note: alphabet is mapped here because there are characters encoded with a value higher
        // than
        // 32,767
        Map<Character, Short> alphabetMap = convertAlphabetToMonotonicSequence(HDFS_2k_CHAR);
        short[] monotonicSequence =
                transformStringIntoMonotonicWithAlphabet(HDFS_2k_CHAR, alphabetMap);
        WaveletFixedBlockBoosting wavelet = new WaveletFixedBlockBoosting(monotonicSequence);

        byte[] serialized =
                Serialization.writeToByteArray(WaveletFixedBlockBoosting::write, wavelet);
        WaveletFixedBlockBoosting deserialized =
                Serialization.readFromByteArray(WaveletFixedBlockBoosting::read, serialized);

        assertEquals(deserialized.hashCode(), wavelet.hashCode());

        Random r = new Random(42);
        for (int i = 0; i < 1_000; i++) {
            int range = r.nextInt(HDFS_2k_CHAR.length);
            int randomPosition = r.nextInt(HDFS_2k_CHAR.length);
            short mappedSymbol = monotonicSequence[randomPosition];
            char symbol = HDFS_2k_CHAR[randomPosition];
            long rank = deserialized.rank(range, mappedSymbol);
            int actualRank = countPreviousOccurrencesOfSymbol(symbol, HDFS_2k_CHAR, range);
            assertThat(rank).isEqualTo(actualRank);
        }
    }

    @Test
    void testComparatorWithEqualItems() {
        Tuple<Long, List<Short>> o1 = new Tuple<>(0L, List.of((short) 0));
        Tuple<Long, List<Short>> o2 = new Tuple<>(0L, List.of((short) 0));
        List<Tuple<Long, List<Short>>> l = new ArrayList<>();
        l.add(o1);
        l.add(o2);
        l.sort(INVERTED_PRIORITY_QUEUE_COMPARATOR_FOR_HUFFMAN);
        assertThat(l.get(0)).isEqualTo(o1);
    }
}
