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
import static com.dynatrace.util.Util.LONGER_TEXT_SHORT;
import static com.dynatrace.util.Util.SMALLER_TEXT;
import static com.dynatrace.util.Util.countPreviousOccurrencesOfSymbol;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Random;
import org.junit.jupiter.api.Test;

public class WaveletTreeTest {

    @Test
    void testWaveletFromSmallText() {
        WaveletTree wavelet = new WaveletTree(SMALLER_TEXT);
        int rank = wavelet.rank(6, 'a');
        assertThat(rank).isEqualTo(2);
        rank = wavelet.rank(SMALLER_TEXT.length, 'a');
        assertThat(rank).isEqualTo(4);
        rank = wavelet.rank(SMALLER_TEXT.length, 'h');
        assertThat(rank).isEqualTo(4);
        rank = wavelet.rank(19, 'i');
        assertThat(rank).isEqualTo(1);
        rank = wavelet.rank(-1, 'i');
        assertThat(rank).isEqualTo(0);
        assertThat(wavelet.access(0)).isEqualTo('a');
        assertThat(wavelet.access(5)).isEqualTo(' ');
        assertThat(wavelet.access(-1)).isEqualTo(null);
        assertThat(wavelet.access(SMALLER_TEXT.length + 1)).isEqualTo(null);
    }

    @Test
    void testWaveletAccessFromLargeCharText() {
        WaveletTree wavelet = new WaveletTree(LONGER_TEXT);
        for (int i = 0; i < LONGER_TEXT_SHORT.length; i++) {
            Character symbol = wavelet.access(i);
            assertThat(symbol).isEqualTo(LONGER_TEXT[i]);
        }
    }

    @Test
    void testWaveletAccessFromLargeStringText() {
        String text = new String(LONGER_TEXT);
        WaveletTree wavelet = new WaveletTree(text);
        for (int i = 0; i < text.length(); i++) {
            Character symbol = wavelet.access(i);
            assertThat(symbol).isEqualTo(text.charAt(i));
        }
    }

    @Test
    void testRankQueryOfNonExistingCharacters() {
        WaveletTree wavelet = new WaveletTree(SMALLER_TEXT);
        int rank = wavelet.rank(22, 'Z');
        assertThat(rank).isEqualTo(0);
    }

    @Test
    void testWaveletRankFromLongText() {
        WaveletTree wavelet = new WaveletTree(LONGER_TEXT);
        Random r = new Random(42);
        for (int i = 0; i < 1_000; i++) {
            int range = r.nextInt(LONGER_TEXT.length);
            char symbol = LONGER_TEXT[r.nextInt(LONGER_TEXT.length)];
            int rank = wavelet.rank(range, symbol);
            int actualRank = countPreviousOccurrencesOfSymbol(symbol, LONGER_TEXT, range);
            assertThat(rank).isEqualTo(actualRank);
        }
    }

    @Test
    void testWaveletRankFromLogs() {
        WaveletTree wavelet = new WaveletTree(HDFS_2k_CHAR);
        Random r = new Random(42);
        for (int i = 0; i < 1_000; i++) {
            int range = r.nextInt(HDFS_2k_CHAR.length);
            char symbol = HDFS_2k_CHAR[r.nextInt(HDFS_2k_CHAR.length)];
            int rank = wavelet.rank(range, symbol);
            int actualRank = countPreviousOccurrencesOfSymbol(symbol, HDFS_2k_CHAR, range);
            assertThat(rank).isEqualTo(actualRank);
        }
    }

    @Test
    void testWaveletRankFromUtf8() {
        char[] text =
                "Chodzą jeże koło wieży, 操據支救数料新方旅日旦时映時智更最月有服未本材来東 spotkał je tam pewien Jerzyk."
                        .toCharArray();

        WaveletTree wavelet = new WaveletTree(text);

        int rank = wavelet.rank(36, 'ł');
        int actualRank = countPreviousOccurrencesOfSymbol('ł', text, 36);
        assertThat(rank).isEqualTo(actualRank);

        rank = wavelet.rank(68, '最');
        actualRank = countPreviousOccurrencesOfSymbol('最', text, 68);
        assertThat(rank).isEqualTo(actualRank);

        rank = wavelet.rank(12, '人'); // does not exist
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

        WaveletTree wavelet = new WaveletTree(text);

        Random r = new Random(8828280);
        for (int i = 0; i < 1_000; i++) {
            // Get a random position (#1), and a random symbol at another random position (#2)
            int pos = r.nextInt(text.length);
            int symbolPos = r.nextInt(text.length);
            char symbol = text[symbolPos];

            // Assert that the random symbol until random position (#1) is the same
            int rank = wavelet.rank(pos, symbol);
            int actualRank = countPreviousOccurrencesOfSymbol(symbol, text, pos);
            assertThat(rank).isEqualTo(actualRank);

            // Assert that accessing the random position (#2) yields the symbol
            Character retrieved = wavelet.access(symbolPos);
            assertThat(retrieved).isEqualTo(symbol);
        }
    }

    @Test
    void testSerializationWithLogQueries() throws IOException, ClassNotFoundException {

        WaveletTree wavelet = new WaveletTree(HDFS_2k_CHAR);

        // write
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(wavelet);
        oos.flush();
        oos.close();

        // read
        byte[] output = baos.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(output);
        ObjectInputStream ois = new ObjectInputStream(bais);
        WaveletTree loaded = (WaveletTree) ois.readObject();
        ois.close();

        Random r = new Random(42);
        for (int i = 0; i < 1_000; i++) {
            int range = r.nextInt(HDFS_2k_CHAR.length);
            char symbol = HDFS_2k_CHAR[r.nextInt(HDFS_2k_CHAR.length)];
            int rank = loaded.rank(range, symbol);
            int actualRank = countPreviousOccurrencesOfSymbol(symbol, HDFS_2k_CHAR, range);
            assertThat(rank).isEqualTo(actualRank);
        }
    }
}
