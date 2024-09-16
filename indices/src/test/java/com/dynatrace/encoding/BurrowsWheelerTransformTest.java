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
package com.dynatrace.encoding;

import static com.dynatrace.encoding.BurrowsWheelerTransform.createBurrowsWheelerTransform;
import static com.dynatrace.util.Util.HDFS_2k_CHAR;
import static com.dynatrace.util.Util.LONGER_TEXT;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class BurrowsWheelerTransformTest {

    @Test
    void testBurrowsWheelerTransformOfWikipediaExample() {
        String string = "BANANA";
        char[] stringBwt = createBurrowsWheelerTransform(string.toCharArray());

        assertThat(new String(stringBwt)).isEqualTo("ANNB\0AA");

        double redundancyOriginal =
                BurrowsWheelerTransform.computeRedundancyOfText(string.toCharArray());
        double redundancyBwt = BurrowsWheelerTransform.computeRedundancyOfText(stringBwt);
        assertThat(redundancyBwt).isGreaterThan(redundancyOriginal);
    }

    @Test
    void testBurrowsWheelerTransformOfWikipediaExampleWithSentinelAlreadyPresent() {
        String string = "BANANA\0";
        char[] stringBwt = createBurrowsWheelerTransform(string.toCharArray());

        assertThat(new String(stringBwt)).isEqualTo("\0ANNB\0AA");

        double redundancyOriginal =
                BurrowsWheelerTransform.computeRedundancyOfText(string.toCharArray());
        double redundancyBwt = BurrowsWheelerTransform.computeRedundancyOfText(stringBwt);
        assertThat(redundancyBwt).isGreaterThan(redundancyOriginal);
    }

    @Test
    void testBurrowsWheelerTransformOfWikipediaExampleAsNumeric() {
        String string = "BANANA";
        char[] stringBwt = createBurrowsWheelerTransform(string.toCharArray());

        assertThat(new String(stringBwt)).isEqualTo("ANNB\0AA");

        short[] numericBwt = new short[stringBwt.length];
        for (int i = 0; i < stringBwt.length; i++) {
            switch (stringBwt[i]) {
                case '\0' -> numericBwt[i] = 0;
                case 'A' -> numericBwt[i] = 1;
                case 'B' -> numericBwt[i] = 2;
                case 'N' -> numericBwt[i] = 3;
            }
        }

        double redundancyOriginal =
                BurrowsWheelerTransform.computeRedundancyOfText(string.toCharArray());
        double redundancyBwt = BurrowsWheelerTransform.computeRedundancyOfText(numericBwt);
        double redundancyStringBwt = BurrowsWheelerTransform.computeRedundancyOfText(stringBwt);
        assertThat(redundancyBwt).isEqualTo(redundancyStringBwt);
        assertThat(redundancyBwt).isGreaterThan(redundancyOriginal);
    }

    @Test
    void testBurrowsWheelerTransformOfShortString() {
        String string = "the_fm_index_is_a_great_data_structure";
        char[] stringBwt = createBurrowsWheelerTransform(string.toCharArray());

        assertThat(new String(stringBwt)).isEqualTo("esteamxa_tedu_nrhrd__t__fiugti_aa\0scrte");

        double redundancyOriginal =
                BurrowsWheelerTransform.computeRedundancyOfText(string.toCharArray());
        double redundancyBwt = BurrowsWheelerTransform.computeRedundancyOfText(stringBwt);
        assertThat(redundancyBwt).isGreaterThan(redundancyOriginal);
    }

    @Test
    void testBurrowsWheelerTransformOfLongString() {
        String string = new String(LONGER_TEXT).replace(" ", "_").replace("\n", "");
        char[] stringBwt = createBurrowsWheelerTransform(string.toCharArray());

        assertThat(new String(stringBwt))
                .isEqualTo(
                        ".nsssseesmgsltnryrrtdlsntytrssnre-hndyrssesndenexyrrs______.e__"
                                + "________._______________\0_tee..en......fefk.e,h,.er...oor.erewnh"
                                + "snndnneodedsyetonnbhdsde,,ds,e,y,,,,egssrtpt,hdsyystdkdn,e,ssdede"
                                + "nsdnafe,naesardeefmelensddsgeageedtdonetytyylgaeheIedIthrmIfnndds"
                                + "sfofndoffhflfes,ssfntnkdaeheenIesrnworoageyfemIhdeeesflfrnaedsfed"
                                + "ye,est,yemyessypeeesntt,hyngysssteey,yedte,lh,sdearysseeafdednrsa"
                                + "edetesIdgtdedffnf,fddsdf,rylrffyrgssttdetptomstrtneknsrereggtdeee"
                                + "etentom,y,,snrsn,seeyeete,ed_____s_______nrtr___lemrrr__ffhhhhe__"
                                + "__irvdcmrtB_tbm___nif__dnrf_mhimmmll_s______________b__p__rhleehe"
                                + "el_mpnw_w___whwwwceepm_hhhhlclcnonv__nnhhs_rwwryai___arumaaaa__oo"
                                + "oau______o_iiuaaoiuranx-ciuiiuiaiaiioeoas_e___n__cc__eaaaaaiaclnn"
                                + "nannenealennaennnlreeeelneaernennnnnnen_n__ornaOolre__aAAaaan_len"
                                + "_nn____n_oeeuemmcrmlnnhlmthcrurbtnshshrhhhcshhvhgthvhkhhlhbhHhlll"
                                + "skhnvshhvshhhrrnlmhsmhHrHhvhnhh_rhhllrrr_finthcincnhttrrrglwwsbws"
                                + "kdnrrveespssddcftntrrhmyg_tsmeeeeeirr_mlsmmlirmmtmcnervldvvlhekdv"
                                + "whhvnhpiwwwhmtpmnthttthdwsssvumsismymtivhrnlndc_urreylbi_wn_ns-_h"
                                + "_Booooooooooollfoooooooooo____r_oouoff________s____ry__iosunnnnnn"
                                + "nnnnaanini_diinnnanrgcttccctcccc____t_s_ttttd_tttttts_t_tsttttttt"
                                + "ttttttttt___tsott__tSttTwwwww_wo___ct______g__f_____swwww__btt_tg"
                                + "rcmglllhhhhhfftrprcftrcrhmesehtmahrtt_hhh________kfdmrmfahaafpsnt"
                                + "ntvwbwrroghsk_dso_ttntttttsttstsblmmfhh_hhhhhhhhhhhcrllhhmdd_rmww"
                                + "wbssllvtttdceceao_aat_olulillalpaluu__ooruueoibtotbcca_lddepeeeeo"
                                + "cpb__iAaaaoeouaooo_r___laaeauucareeepliliebioooroo_eoooo___aioaii"
                                + "o__muhasm_rllle_ao_dd_diiuam_e_eee_oeooii___eeeooooaiiiiaeoooewww"
                                + "iowIooweeiiaiiaaoaoi_iir__aiaiaaaeaaaaaaaaaaauoAauaaaaieeioiooiis"
                                + "iooeii_kiiiiiiiiiiooiiiioga__ooogoeeoeiieeeueeeeieeieiiaatTtthtth"
                                + "tttl__dc_slslr__y____r:___s________p__sBtdtpoc_hhpHfHHHrdrrrcwwwh"
                                + "sdccciiiiipsiiiiiy__d_srmsiiimibhf_fffftsamwffhmhhpmnn__rmmmmdsww"
                                + "rc_i_llrpphl____sruuumerom_up_mmeeslu_-_a_d_ideeeeeeeeoeoeoeeueee"
                                + "oooeaeoiicttttetdoueaauoueeouee___ptpat_e_IIrTpe_eeerufedcTaa_Goe"
                                + "aooeteffFftbahdeoaeeeiadtteeeoeasakensneeintgaiieeuruasiiaidaaige"
                                + "'anrsiiiiaeeaiaeynaetise_iionalei___mmo_bbbnp_gs___ii_w_nssuoni__"
                                + "_r__naab_i_eeeeeeiaroeuya_ei_ne_i____e_eninauluosnuainuascioaeuhn"
                                + "nbInas_naaecsfaa_tnslsfaiii_____________________________aoo_o____"
                                + "_rls__cainionoasociocAtt____n_____cts__xnSnssnlsaiiassaaeiedosdss"
                                + "tglqsrogcoofcsrso_o___c_otmttbo_rjbbBbo_roiaaa_eeroeeirioe______a"
                                + "l__to___oo_____,____oa_d_ooooo______oeeetlrlllmllrmMlgdlnMrllbbet"
                                + "lne_adeam");

        double redundancyOriginal =
                BurrowsWheelerTransform.computeRedundancyOfText(string.toCharArray());
        double redundancyBwt = BurrowsWheelerTransform.computeRedundancyOfText(stringBwt);
        assertThat(redundancyBwt).isGreaterThan(redundancyOriginal);
    }

    @Test
    void testBurrowsWheelerTransformOfSyntheticExample() {
        char[] synthetic = new char[1_000_000];
        for (int i = 0; i < synthetic.length; i++) {
            if (i % 2 == 0) {
                synthetic[i] = 'a';
            } else {
                synthetic[i] = 'b';
            }
        }
        char[] stringBwt = createBurrowsWheelerTransform(synthetic);

        double redundancyOriginal = BurrowsWheelerTransform.computeRedundancyOfText(synthetic);
        double redundancyBwt = BurrowsWheelerTransform.computeRedundancyOfText(stringBwt);
        assertThat(redundancyBwt).isGreaterThan(redundancyOriginal);
    }

    @Test
    void testBurrowsWheelerTransformOfLogs() {
        char[] stringBwt = createBurrowsWheelerTransform(HDFS_2k_CHAR);

        double redundancyOriginal = BurrowsWheelerTransform.computeRedundancyOfText(HDFS_2k_CHAR);
        double redundancyBwt = BurrowsWheelerTransform.computeRedundancyOfText(stringBwt);
        assertThat(redundancyBwt).isGreaterThan(redundancyOriginal);
    }

    @Test
    void testShouldThrowExceptionFromReachingCharacterSetLimit() {
        char[] textWithTooManySymbols = new char[Short.MAX_VALUE + 1];
        for (int i = 0; i < textWithTooManySymbols.length; i++) {
            textWithTooManySymbols[i] = (char) i;
        }
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> createBurrowsWheelerTransform(textWithTooManySymbols));
        assertThat(exception.getMessage())
                .isEqualTo("Charset has more than 32767 different characters.");
    }
}
