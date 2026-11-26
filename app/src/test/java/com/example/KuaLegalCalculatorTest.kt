package com.example

import com.example.util.AgeLegalTier
import com.example.util.DivorceType
import com.example.util.DukhulStatus
import com.example.util.IddahMarriageEligibility
import com.example.util.KuaLegalCalculator
import com.example.util.MenstruationStatus
import com.example.util.WaliVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KuaLegalCalculatorTest {

    @Test
    fun waliNikah_moreThanSixHijriMonths_returnsWaliNasab() {
        // 185 days (> 6 Hijri months ~177 days)
        val result = KuaLegalCalculator.calculateWaliNikah(
            parentMarriageIso = "2001-01-01",
            brideBirthIso = "2001-07-05"
        )
        assertNotNull(result)
        assertEquals(WaliVerdict.WALI_NASAB, result!!.verdict)
        assertTrue(result.hijriTotalMonths >= 6)
    }

    @Test
    fun waliNikah_lessThanSixHijriMonths_returnsWaliHakimKhi100() {
        // 150 days (< 6 Hijri months)
        val result = KuaLegalCalculator.calculateWaliNikah(
            parentMarriageIso = "2001-01-01",
            brideBirthIso = "2001-05-31"
        )
        assertNotNull(result)
        assertEquals(WaliVerdict.WALI_HAKIM, result!!.verdict)
        assertTrue(result.legalBasis.contains("KHI Pasal 100"))
    }

    @Test
    fun waliNikah_bornBeforeParentMarriage_returnsWaliHakim() {
        val result = KuaLegalCalculator.calculateWaliNikah(
            parentMarriageIso = "2001-06-10",
            brideBirthIso = "2001-05-01"
        )
        assertNotNull(result)
        assertEquals(WaliVerdict.WALI_HAKIM, result!!.verdict)
        assertTrue(result.isBornBeforeMarriage)
    }

    @Test
    fun iddah_ceraiMatiNotPregnant_130Days() {
        val stillInIddah = KuaLegalCalculator.calculateMasaIddah(
            divorceType = DivorceType.CERAI_MATI,
            isPregnant = false,
            dukhulStatus = DukhulStatus.QABLA_DUKHUL,
            menstruationStatus = MenstruationStatus.MASIH_HAID,
            startDateIso = "2026-01-01",
            plannedWeddingIso = "2026-05-10" // 129 days
        )
        assertNotNull(stillInIddah)
        assertEquals(130, stillInIddah!!.durationDays)
        assertEquals(IddahMarriageEligibility.STILL_IN_IDDAH, stillInIddah.eligibility)

        val eligible = KuaLegalCalculator.calculateMasaIddah(
            divorceType = DivorceType.CERAI_MATI,
            isPregnant = false,
            dukhulStatus = DukhulStatus.BADA_DUKHUL,
            menstruationStatus = MenstruationStatus.MASIH_HAID,
            startDateIso = "2026-01-01",
            plannedWeddingIso = "2026-05-12" // 131 days
        )
        assertNotNull(eligible)
        assertEquals(IddahMarriageEligibility.ELIGIBLE, eligible!!.eligibility)
    }

    @Test
    fun iddah_pregnant_alwaysWaitsUntilBirth() {
        val result = KuaLegalCalculator.calculateMasaIddah(
            divorceType = DivorceType.CERAI_GUGAT,
            isPregnant = true,
            dukhulStatus = DukhulStatus.BADA_DUKHUL,
            menstruationStatus = MenstruationStatus.MASIH_HAID,
            startDateIso = "2026-01-01",
            plannedWeddingIso = "2026-12-01"
        )
        assertNotNull(result)
        assertEquals(IddahMarriageEligibility.WAIT_FOR_BIRTH, result!!.eligibility)
    }

    @Test
    fun iddah_ceraiHidupQablaDukhul_zeroDays() {
        val result = KuaLegalCalculator.calculateMasaIddah(
            divorceType = DivorceType.CERAI_TALAK,
            isPregnant = false,
            dukhulStatus = DukhulStatus.QABLA_DUKHUL,
            menstruationStatus = MenstruationStatus.MASIH_HAID,
            startDateIso = "2026-03-01",
            plannedWeddingIso = "2026-03-05"
        )
        assertNotNull(result)
        assertEquals(0, result!!.durationDays)
        assertEquals(IddahMarriageEligibility.ELIGIBLE, result.eligibility)
    }

    @Test
    fun iddah_ceraiHidupBadaDukhul_90DaysMinimum() {
        val result = KuaLegalCalculator.calculateMasaIddah(
            divorceType = DivorceType.CERAI_GUGAT,
            isPregnant = false,
            dukhulStatus = DukhulStatus.BADA_DUKHUL,
            menstruationStatus = MenstruationStatus.MASIH_HAID,
            startDateIso = "2026-01-01",
            plannedWeddingIso = "2026-04-02" // 91 days
        )
        assertNotNull(result)
        assertEquals(90, result!!.durationDays)
        assertEquals(IddahMarriageEligibility.ELIGIBLE, result.eligibility)
    }

    @Test
    fun coupleAge_allThreeLegalTiersVerified() {
        val result = KuaLegalCalculator.calculateCoupleAge(
            groomBirthIso = "2004-05-01", // 22 years -> >= 21
            brideBirthIso = "2007-06-01", // 18 years 11 months -> < 19 (Dispensasi Kawin)
            weddingDateIso = "2026-05-15"
        )
        assertNotNull(result)
        assertEquals(AgeLegalTier.AGE_21_PLUS, result!!.groom.tier)
        assertEquals(AgeLegalTier.UNDER_19, result.bride.tier)
        assertEquals(AgeLegalTier.UNDER_19, result.overallTier)

        val needParents = KuaLegalCalculator.calculateCoupleAge(
            groomBirthIso = "2006-01-01", // 20 years -> 19..20 (Izin Orang Tua N5)
            brideBirthIso = "2003-01-01", // 23 years -> >= 21
            weddingDateIso = "2026-05-15"
        )
        assertNotNull(needParents)
        assertEquals(AgeLegalTier.AGE_19_TO_20, needParents!!.groom.tier)
        assertEquals(AgeLegalTier.AGE_21_PLUS, needParents.bride.tier)
        assertEquals(AgeLegalTier.AGE_19_TO_20, needParents.overallTier)
    }
}
