package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

data class SimpleYmd(
    val year: Int,
    val month: Int, // 1..12
    val day: Int    // 1..31
) {
    fun toIsoString(): String =
        "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

data class HijriDate(
    val year: Int,
    val month: Int, // 1..12
    val day: Int    // 1..30
) {
    val monthName: String
        get() = HIJRI_MONTH_NAMES.getOrElse(month - 1) { "Muharram" }

    fun formatted(): String = "$day $monthName $year H"

    companion object {
        val HIJRI_MONTH_NAMES = listOf(
            "Muharram",
            "Safar",
            "Rabiul Awal",
            "Rabiul Akhir",
            "Jumadil Awal",
            "Jumadil Akhir",
            "Rajab",
            "Sya'ban",
            "Ramadhan",
            "Syawal",
            "Dzulkaidah",
            "Dzulhijjah"
        )
    }
}

enum class WaliVerdict(val title: String, val badgeLabel: String) {
    WALI_NASAB("Wali Nasab", "Sah Wali Nasab"),
    WALI_HAKIM("Wali Hakim (KHI Pasal 100)", "Wajib Wali Hakim")
}

data class WaliNikahResult(
    val parentMarriageIso: String,
    val brideBirthIso: String,
    val parentMarriageHijri: HijriDate,
    val brideBirthHijri: HijriDate,
    val totalDays: Int,
    val hijriTotalMonths: Int,
    val hijriRemainingDays: Int,
    val isBornBeforeMarriage: Boolean,
    val verdict: WaliVerdict,
    val summary: String,
    val legalBasis: String
)

enum class DivorceType(val label: String, val subtitle: String) {
    CERAI_MATI("Cerai Mati", "Suami meninggal dunia"),
    CERAI_TALAK("Cerai Talak", "Cerai hidup (ikrar talak suami)"),
    CERAI_GUGAT("Cerai Gugat", "Cerai hidup (gugatan istri)")
}

enum class DukhulStatus(val label: String, val subtitle: String) {
    BADA_DUKHUL("Ba'da ad-Dukhul", "Sudah pernah berhubungan suami-istri"),
    QABLA_DUKHUL("Qabla ad-Dukhul", "Belum pernah berhubungan suami-istri")
}

enum class MenstruationStatus(val label: String, val subtitle: String) {
    MASIH_HAID("Masih Haid", "3 kali suci (minimal 90 hari menurut KHI)"),
    TIDAK_HAID("Tidak Haid", "Menopause / belum haid (3 bulan / 90 hari)")
}

enum class IddahMarriageEligibility {
    ELIGIBLE,
    STILL_IN_IDDAH,
    WAIT_FOR_BIRTH
}

data class IddahResult(
    val divorceType: DivorceType,
    val isPregnant: Boolean,
    val dukhulStatus: DukhulStatus,
    val menstruationStatus: MenstruationStatus,
    val startDateIso: String,
    val plannedWeddingIso: String,
    val durationDays: Int?, // null when waiting until childbirth
    val durationLabel: String,
    val iddahEndDateIso: String?,
    val earliestAllowedWeddingIso: String?,
    val eligibility: IddahMarriageEligibility,
    val daysRemainingAtWedding: Int,
    val verdictTitle: String,
    val explanation: String,
    val legalBasis: String
)

enum class AgeLegalTier(val badgeLabel: String, val title: String) {
    UNDER_19("Di Bawah 19 Tahun", "Wajib Dispensasi Kawin Pengadilan"),
    AGE_19_TO_20("19 – 20 Tahun", "Memenuhi Minimal • Perlu Izin Orang Tua (N5)"),
    AGE_21_PLUS("21 Tahun ke Atas", "Memenuhi Syarat Penuh (Mandiri)")
}

data class PersonAgeResult(
    val roleLabel: String,
    val birthDateIso: String,
    val weddingDateIso: String,
    val years: Int,
    val months: Int,
    val days: Int,
    val isInvalidDate: Boolean,
    val tier: AgeLegalTier,
    val turns19DateIso: String,
    val explanation: String
) {
    fun formattedAge(): String = "$years tahun $months bulan $days hari"
}

data class CoupleAgeResult(
    val weddingDateIso: String,
    val groom: PersonAgeResult,
    val bride: PersonAgeResult,
    val overallTier: AgeLegalTier,
    val overallTitle: String,
    val overallSummary: String
)

object KuaLegalCalculator {

    fun parseIso(iso: String): SimpleYmd? {
        val parts = iso.trim().split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        if (m !in 1..12 || d !in 1..31 || y < 1900 || y > 2200) return null
        return SimpleYmd(y, m, d)
    }

    fun isoToUtcMillis(iso: String): Long? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }
            sdf.parse(iso)?.time
        } catch (_: Exception) {
            null
        }
    }

    fun utcMillisToIso(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date(millis))
    }

    /**
     * Converts Gregorian date to Julian Day Number (exact proleptic Gregorian formula).
     */
    fun gregorianToJdn(year: Int, month: Int, day: Int): Int {
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        return day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
    }

    /**
     * Converts Julian Day Number to Tabular Islamic (Hijriyah / Qamariyah) date.
     * Standard Kuwaiti / Civil Hijriyah algorithm (accurate across all Android API levels 24+).
     */
    fun jdnToHijri(jdn: Int): HijriDate {
        var l = jdn - 1948440 + 10632
        val n = (l - 1) / 10631
        l = l - 10631 * n + 354
        val j = ((10985 - l) / 5316) * ((50 * l) / 17719) + (l / 5670) * ((43 * l) / 15238)
        l = l - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29
        val month = (24 * l) / 709
        val day = l - (709 * month) / 24
        val year = 30 * n + j - 30
        return HijriDate(year = year, month = month.coerceIn(1, 12), day = day.coerceIn(1, 30))
    }

    fun gregorianToHijri(ymd: SimpleYmd): HijriDate {
        return jdnToHijri(gregorianToJdn(ymd.year, ymd.month, ymd.day))
    }

    fun addDaysToIso(iso: String, daysToAdd: Int): String {
        val ymd = parseIso(iso) ?: return iso
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(ymd.year, ymd.month - 1, ymd.day)
            add(Calendar.DAY_OF_MONTH, daysToAdd)
        }
        return SimpleYmd(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH)
        ).toIsoString()
    }

    fun addYearsToIso(iso: String, yearsToAdd: Int): String {
        val ymd = parseIso(iso) ?: return iso
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(ymd.year, ymd.month - 1, ymd.day)
            add(Calendar.YEAR, yearsToAdd)
        }
        return SimpleYmd(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH)
        ).toIsoString()
    }

    fun daysBetweenIso(startIso: String, endIso: String): Int? {
        val s = parseIso(startIso) ?: return null
        val e = parseIso(endIso) ?: return null
        return gregorianToJdn(e.year, e.month, e.day) - gregorianToJdn(s.year, s.month, s.day)
    }

    /**
     * 1. KALKULATOR CEK WALI NIKAH (HITUNGAN HIJRIYAH)
     * - Menghitung jarak dari Tanggal Nikah Sah Orang Tua (Buku Nikah Ortu) ke Tanggal Lahir Catin Perempuan
     *   secara kalender Hijriyah (Qamariyah).
     * - Harus lebih dari 6 bulan Hijriyah -> Wali Nasab.
     * - Kurang dari (atau tidak mencapai lebih dari) 6 bulan Hijriyah -> Wali Hakim (KHI Pasal 100).
     */
    fun calculateWaliNikah(
        parentMarriageIso: String,
        brideBirthIso: String
    ): WaliNikahResult? {
        val marriageYmd = parseIso(parentMarriageIso) ?: return null
        val birthYmd = parseIso(brideBirthIso) ?: return null

        val marriageJdn = gregorianToJdn(marriageYmd.year, marriageYmd.month, marriageYmd.day)
        val birthJdn = gregorianToJdn(birthYmd.year, birthYmd.month, birthYmd.day)
        val totalDays = birthJdn - marriageJdn

        val marriageHijri = jdnToHijri(marriageJdn)
        val birthHijri = jdnToHijri(birthJdn)

        if (totalDays < 0) {
            return WaliNikahResult(
                parentMarriageIso = parentMarriageIso,
                brideBirthIso = brideBirthIso,
                parentMarriageHijri = marriageHijri,
                brideBirthHijri = birthHijri,
                totalDays = totalDays,
                hijriTotalMonths = 0,
                hijriRemainingDays = 0,
                isBornBeforeMarriage = true,
                verdict = WaliVerdict.WALI_HAKIM,
                summary = "Catin perempuan lahir ${abs(totalDays)} hari SEBELUM akad nikah sah orang tua. Status wali nikah adalah Wali Hakim.",
                legalBasis = "KHI Pasal 100: Anak yang lahir di luar perkawinan hanya mempunyai hubungan nasab dengan ibunya dan keluarga ibunya (tidak mempunyai hubungan nasab dengan ayahnya), sehingga wali nikahnya adalah Wali Hakim."
            )
        }

        // Exact Hijriyah month & day difference (Qamariyah)
        var diffMonths = (birthHijri.year - marriageHijri.year) * 12 + (birthHijri.month - marriageHijri.month)
        var diffDays = birthHijri.day - marriageHijri.day
        if (diffDays < 0) {
            diffMonths -= 1
            // Previous Hijriyah month length (~29.5 -> alternating 30 for odd months, 29 for even)
            val prevMonth = if (birthHijri.month == 1) 12 else birthHijri.month - 1
            val prevMonthDays = if (prevMonth % 2 == 1) 30 else 29
            diffDays += prevMonthDays
        }
        if (diffMonths < 0) {
            diffMonths = 0
            diffDays = 0
        }

        // Syarat fikih & KUA: lebih dari 6 bulan Hijriyah (aqallul hamli 6 bulan + 2 lahzah)
        val isMoreThanSixHijriMonths = diffMonths > 6 || (diffMonths == 6 && diffDays > 0)

        return if (isMoreThanSixHijriMonths) {
            WaliNikahResult(
                parentMarriageIso = parentMarriageIso,
                brideBirthIso = brideBirthIso,
                parentMarriageHijri = marriageHijri,
                brideBirthHijri = birthHijri,
                totalDays = totalDays,
                hijriTotalMonths = diffMonths,
                hijriRemainingDays = diffDays,
                isBornBeforeMarriage = false,
                verdict = WaliVerdict.WALI_NASAB,
                summary = "Jarak kelahiran adalah $diffMonths bulan $diffDays hari Hijriyah ($totalDays hari), yaitu LEBIH DARI 6 bulan Hijriyah. Maka wali nikahnya adalah Wali Nasab.",
                legalBasis = "Memenuhi batas minimal masa kehamilan (aqallul hamli) lebih dari 6 bulan Qamariyah/Hijriyah sejak akad nikah sah orang tua. Ayah kandung atau wali nasab sesuai urutan KHI Pasal 21 sah menjadi wali nikah."
            )
        } else {
            WaliNikahResult(
                parentMarriageIso = parentMarriageIso,
                brideBirthIso = brideBirthIso,
                parentMarriageHijri = marriageHijri,
                brideBirthHijri = birthHijri,
                totalDays = totalDays,
                hijriTotalMonths = diffMonths,
                hijriRemainingDays = diffDays,
                isBornBeforeMarriage = false,
                verdict = WaliVerdict.WALI_HAKIM,
                summary = "Jarak kelahiran adalah $diffMonths bulan $diffDays hari Hijriyah ($totalDays hari), yakni TIDAK LEBIH DARI 6 bulan Hijriyah sejak akad nikah sah orang tua. Status wali nikah adalah Wali Hakim.",
                legalBasis = "KHI Pasal 100: Anak yang lahir kurang dari 6 bulan Hijriyah dari akad nikah sah orang tua tidak mempunyai hubungan nasab dengan ayahnya (hanya bernasab ke ibu dan keluarga ibu), sehingga yang menjadi wali nikah adalah Wali Hakim (Kepala KUA)."
            )
        }
    }

    /**
     * 2. KALKULATOR MASA IDDAH (KHI Pasal 153 & Fikih Munakahat)
     */
    fun calculateMasaIddah(
        divorceType: DivorceType,
        isPregnant: Boolean,
        dukhulStatus: DukhulStatus,
        menstruationStatus: MenstruationStatus,
        startDateIso: String,
        plannedWeddingIso: String
    ): IddahResult? {
        if (parseIso(startDateIso) == null || parseIso(plannedWeddingIso) == null) return null

        // Case A: Sedang Hamil (berlaku untuk Cerai Mati maupun Cerai Talak / Cerai Gugat)
        if (isPregnant) {
            return IddahResult(
                divorceType = divorceType,
                isPregnant = true,
                dukhulStatus = dukhulStatus,
                menstruationStatus = menstruationStatus,
                startDateIso = startDateIso,
                plannedWeddingIso = plannedWeddingIso,
                durationDays = null,
                durationLabel = "Sampai Melahirkan",
                iddahEndDateIso = null,
                earliestAllowedWeddingIso = null,
                eligibility = IddahMarriageEligibility.WAIT_FOR_BIRTH,
                daysRemainingAtWedding = 0,
                verdictTitle = "Menunggu Persalinan (Iddah Sampai Melahirkan)",
                explanation = "Untuk perempuan dalam keadaan hamil (${divorceType.label}), masa iddah berakhir setelah melahirkan anak yang dikandungnya. Pernikahan baru hanya dapat dilangsungkan setelah melahirkan.",
                legalBasis = "QS. At-Talaq ayat 4 & KHI Pasal 153 ayat (2) huruf c: Apabila perkawinan putus karena perceraian atau kematian sedang janda tersebut dalam keadaan hamil, waktu tunggu ditetapkan sampai melahirkan."
            )
        }

        // Case B: Cerai Mati (Tidak Hamil) -> 4 bulan 10 hari (130 hari), berlaku baik ba'da maupun qabla dukhul
        if (divorceType == DivorceType.CERAI_MATI) {
            val durationDays = 130
            val iddahEndIso = addDaysToIso(startDateIso, durationDays)
            val earliestWeddingIso = addDaysToIso(startDateIso, durationDays + 1)
            val elapsedAtWedding = daysBetweenIso(startDateIso, plannedWeddingIso) ?: 0
            val remaining = (durationDays + 1) - elapsedAtWedding
            val isEligible = elapsedAtWedding > durationDays

            return IddahResult(
                divorceType = divorceType,
                isPregnant = false,
                dukhulStatus = dukhulStatus,
                menstruationStatus = menstruationStatus,
                startDateIso = startDateIso,
                plannedWeddingIso = plannedWeddingIso,
                durationDays = durationDays,
                durationLabel = "4 Bulan 10 Hari (130 Hari)",
                iddahEndDateIso = iddahEndIso,
                earliestAllowedWeddingIso = earliestWeddingIso,
                eligibility = if (isEligible) IddahMarriageEligibility.ELIGIBLE else IddahMarriageEligibility.STILL_IN_IDDAH,
                daysRemainingAtWedding = remaining.coerceAtLeast(0),
                verdictTitle = if (isEligible) {
                    "Masa Iddah Telah Selesai — Boleh Menikah"
                } else {
                    "Masih Dalam Masa Iddah (Kurang ${remaining.coerceAtLeast(1)} Hari)"
                },
                explanation = if (isEligible) {
                    "Rencana tanggal nikah sudah melewati masa iddah cerai mati 4 bulan 10 hari (130 hari). Catatan: cerai mati tetap beriddah 130 hari baik ba'da dukhul maupun qabla dukhul."
                } else {
                    "Rencana tanggal nikah masih berada dalam masa iddah cerai mati 4 bulan 10 hari (130 hari). Akad nikah baru dapat dilaksanakan paling cepat mulai tanggal yang direkomendasikan."
                },
                legalBasis = "QS. Al-Baqarah ayat 234 & KHI Pasal 153 ayat (2) huruf a: Apabila perkawinan putus karena kematian, waktu tunggu ditetapkan 130 (seratus tiga puluh) hari (4 bulan 10 hari)."
            )
        }

        // Case C: Cerai Hidup (Cerai Talak / Cerai Gugat) + Qabla ad-Dukhul (Belum Digauli) -> 0 hari
        if (dukhulStatus == DukhulStatus.QABLA_DUKHUL) {
            val diff = daysBetweenIso(startDateIso, plannedWeddingIso) ?: 0
            val isAfterDivorce = diff >= 0
            return IddahResult(
                divorceType = divorceType,
                isPregnant = false,
                dukhulStatus = dukhulStatus,
                menstruationStatus = menstruationStatus,
                startDateIso = startDateIso,
                plannedWeddingIso = plannedWeddingIso,
                durationDays = 0,
                durationLabel = "Tidak Ada Masa Iddah (0 Hari)",
                iddahEndDateIso = startDateIso,
                earliestAllowedWeddingIso = startDateIso,
                eligibility = if (isAfterDivorce) IddahMarriageEligibility.ELIGIBLE else IddahMarriageEligibility.STILL_IN_IDDAH,
                daysRemainingAtWedding = if (isAfterDivorce) 0 else abs(diff),
                verdictTitle = if (isAfterDivorce) {
                    "Tidak Ada Masa Iddah — Boleh Menikah"
                } else {
                    "Tanggal Nikah Sebelum Tanggal Putus Cerai"
                },
                explanation = if (isAfterDivorce) {
                    "Karena ${divorceType.label} terjadi sebelum berhubungan suami-istri (qabla ad-dukhul), maka tidak ada masa iddah dan diperbolehkan menikah setelah Akta Cerai / putusan berkekuatan hukum tetap."
                } else {
                    "Rencana tanggal nikah lebih awal dari tanggal putus cerai. Pastikan akad dilakukan setelah perceraian sah secara hukum."
                },
                legalBasis = "QS. Al-Ahzab ayat 49 & KHI Pasal 153 ayat (4): Bagi perkawinan yang putus karena perceraian sedang antara janda tersebut dengan bekas suaminya qabla al-dukhul, tidak ada waktu tunggu (iddah)."
            )
        }

        // Case D: Cerai Hidup (Cerai Talak / Cerai Gugat) + Ba'da ad-Dukhul + Tidak Hamil -> 90 hari minimal (KHI)
        val durationDays = 90
        val iddahEndIso = addDaysToIso(startDateIso, durationDays)
        val earliestWeddingIso = addDaysToIso(startDateIso, durationDays + 1)
        val elapsedAtWedding = daysBetweenIso(startDateIso, plannedWeddingIso) ?: 0
        val remaining = (durationDays + 1) - elapsedAtWedding
        val isEligible = elapsedAtWedding > durationDays

        val durationLabel = if (menstruationStatus == MenstruationStatus.MASIH_HAID) {
            "3 Kali Suci (Min. 90 Hari KHI)"
        } else {
            "3 Bulan (Min. 90 Hari KHI)"
        }

        val explanation = if (menstruationStatus == MenstruationStatus.MASIH_HAID) {
            if (isEligible) {
                "Rencana tanggal nikah sudah melewati batas minimal 90 hari menurut KHI. Pastikan pula secara syar'i calon mempelai wanita telah menyelesaikan 3 kali masa suci (quru')."
            } else {
                "Rencana tanggal nikah belum melewati batas minimal 90 hari untuk masa iddah 3 kali suci (${divorceType.label}). Akad nikah belum dapat dilangsungkan."
            }
        } else {
            if (isEligible) {
                "Rencana tanggal nikah sudah melewati masa iddah 3 bulan (minimal 90 hari menurut KHI) untuk kondisi tidak haid/menopause."
            } else {
                "Rencana tanggal nikah masih berada dalam masa iddah 3 bulan (minimal 90 hari) untuk kondisi tidak haid/menopause."
            }
        }

        val legalBasis = if (menstruationStatus == MenstruationStatus.MASIH_HAID) {
            "KHI Pasal 153 ayat (2) huruf b: Apabila perkawinan putus karena perceraian, waktu tunggu bagi yang masih haid ditetapkan 3 (tiga) kali suci dengan sekurang-kurangnya 90 (sembilan puluh) hari."
        } else {
            "QS. At-Talaq ayat 4 & KHI Pasal 153 ayat (2) huruf b: Bagi yang tidak haid (menopause/belum haid), waktu tunggu ditetapkan 3 (tiga) bulan atau sekurang-kurangnya 90 (sembilan puluh) hari."
        }

        return IddahResult(
            divorceType = divorceType,
            isPregnant = false,
            dukhulStatus = dukhulStatus,
            menstruationStatus = menstruationStatus,
            startDateIso = startDateIso,
            plannedWeddingIso = plannedWeddingIso,
            durationDays = durationDays,
            durationLabel = durationLabel,
            iddahEndDateIso = iddahEndIso,
            earliestAllowedWeddingIso = earliestWeddingIso,
            eligibility = if (isEligible) IddahMarriageEligibility.ELIGIBLE else IddahMarriageEligibility.STILL_IN_IDDAH,
            daysRemainingAtWedding = remaining.coerceAtLeast(0),
            verdictTitle = if (isEligible) {
                "Masa Iddah Minimal Terpenuhi — Boleh Menikah"
            } else {
                "Masih Dalam Masa Iddah (Kurang ${remaining.coerceAtLeast(1)} Hari)"
            },
            explanation = explanation,
            legalBasis = legalBasis
        )
    }

    /**
     * 3. KALKULATOR UMUR CATIN (Calon Suami & Calon Istri Sekaligus)
     * - Minimal 19 tahun untuk pria dan wanita (UU No. 16 Tahun 2019).
     * - Di bawah 19 tahun: Wajib dispensasi kawin ke Pengadilan Agama (Muslim) / Pengadilan Negeri (non-Muslim).
     * - Di bawah 21 tahun (19..20 tahun): Administrasi pencatatan nikah memerlukan izin/persetujuan orang tua (Model N5).
     */
    fun calculatePersonAge(
        roleLabel: String,
        birthDateIso: String,
        weddingDateIso: String
    ): PersonAgeResult? {
        val birth = parseIso(birthDateIso) ?: return null
        val wedding = parseIso(weddingDateIso) ?: return null

        val turns19Iso = addYearsToIso(birthDateIso, 19)
        val totalDays = daysBetweenIso(birthDateIso, weddingDateIso) ?: return null

        if (totalDays < 0) {
            return PersonAgeResult(
                roleLabel = roleLabel,
                birthDateIso = birthDateIso,
                weddingDateIso = weddingDateIso,
                years = 0,
                months = 0,
                days = 0,
                isInvalidDate = true,
                tier = AgeLegalTier.UNDER_19,
                turns19DateIso = turns19Iso,
                explanation = "Tanggal lahir $roleLabel lebih besar dari tanggal rencana nikah. Periksa kembali tanggal yang dimasukkan."
            )
        }

        var years = wedding.year - birth.year
        var months = wedding.month - birth.month
        var days = wedding.day - birth.day

        if (days < 0) {
            months -= 1
            val prevCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(wedding.year, wedding.month - 1, 1)
                add(Calendar.DAY_OF_MONTH, -1)
            }
            days += prevCal.get(Calendar.DAY_OF_MONTH)
        }
        if (months < 0) {
            years -= 1
            months += 12
        }

        val tier = when {
            years < 19 -> AgeLegalTier.UNDER_19
            years < 21 -> AgeLegalTier.AGE_19_TO_20
            else -> AgeLegalTier.AGE_21_PLUS
        }

        val explanation = when (tier) {
            AgeLegalTier.UNDER_19 ->
                "Usia $roleLabel pada hari akad masih di bawah batas minimal 19 tahun. Wajib mengajukan dispensasi kawin ke Pengadilan Agama (Muslim) atau Pengadilan Negeri (non-Muslim) dengan alasan sangat mendesak serta bukti pendukung yang cukup."
            AgeLegalTier.AGE_19_TO_20 ->
                "Usia $roleLabel telah memenuhi batas minimal 19 tahun. Karena belum berusia 21 tahun, secara administratif pencatatan nikah memerlukan izin/persetujuan orang tua (Formulir N5)."
            AgeLegalTier.AGE_21_PLUS ->
                "Usia $roleLabel telah mencapai 21 tahun ke atas. Memenuhi syarat usia perkawinan dan dapat mendaftar nikah secara mandiri tanpa dispensasi kawin maupun izin orang tua (N5)."
        }

        return PersonAgeResult(
            roleLabel = roleLabel,
            birthDateIso = birthDateIso,
            weddingDateIso = weddingDateIso,
            years = years,
            months = months,
            days = days,
            isInvalidDate = false,
            tier = tier,
            turns19DateIso = turns19Iso,
            explanation = explanation
        )
    }

    fun calculateCoupleAge(
        groomBirthIso: String,
        brideBirthIso: String,
        weddingDateIso: String
    ): CoupleAgeResult? {
        val groom = calculatePersonAge("Calon Suami", groomBirthIso, weddingDateIso) ?: return null
        val bride = calculatePersonAge("Calon Istri", brideBirthIso, weddingDateIso) ?: return null

        val overallTier = when {
            groom.tier == AgeLegalTier.UNDER_19 || bride.tier == AgeLegalTier.UNDER_19 -> AgeLegalTier.UNDER_19
            groom.tier == AgeLegalTier.AGE_19_TO_20 || bride.tier == AgeLegalTier.AGE_19_TO_20 -> AgeLegalTier.AGE_19_TO_20
            else -> AgeLegalTier.AGE_21_PLUS
        }

        val overallTitle = when (overallTier) {
            AgeLegalTier.UNDER_19 -> "Perlu Dispensasi Kawin Pengadilan"
            AgeLegalTier.AGE_19_TO_20 -> "Memenuhi Usia Minimal • Perlu Izin Orang Tua (N5)"
            AgeLegalTier.AGE_21_PLUS -> "Kedua Catin Memenuhi Syarat Usia Penuh"
        }

        val overallSummary = when {
            groom.isInvalidDate || bride.isInvalidDate ->
                "Periksa kembali tanggal lahir calon pengantin karena lebih besar daripada tanggal nikah."
            groom.tier == AgeLegalTier.UNDER_19 && bride.tier == AgeLegalTier.UNDER_19 ->
                "Kedua calon mempelai berusia di bawah 19 tahun. Wajib melampirkan penetapan Dispensasi Kawin dari Pengadilan (PA/PN) sebelum pencatatan nikah di KUA."
            groom.tier == AgeLegalTier.UNDER_19 ->
                "Calon Suami berusia di bawah 19 tahun (${groom.formattedAge()}). Wajib melampirkan Dispensasi Kawin dari Pengadilan (PA/PN)."
            bride.tier == AgeLegalTier.UNDER_19 ->
                "Calon Istri berusia di bawah 19 tahun (${bride.formattedAge()}). Wajib melampirkan Dispensasi Kawin dari Pengadilan (PA/PN)."
            groom.tier == AgeLegalTier.AGE_19_TO_20 && bride.tier == AgeLegalTier.AGE_19_TO_20 ->
                "Kedua calon mempelai telah ≥ 19 tahun namun di bawah 21 tahun. Keduanya memerlukan Surat Izin Orang Tua (Formulir N5) saat pendaftaran nikah."
            groom.tier == AgeLegalTier.AGE_19_TO_20 ->
                "Calon Suami berusia di bawah 21 tahun (${groom.formattedAge()}). Diperlukan Surat Izin Orang Tua (N5) untuk Calon Suami."
            bride.tier == AgeLegalTier.AGE_19_TO_20 ->
                "Calon Istri berusia di bawah 21 tahun (${bride.formattedAge()}). Diperlukan Surat Izin Orang Tua (N5) untuk Calon Istri."
            else ->
                "Calon Suami (${groom.years} thn) dan Calon Istri (${bride.years} thn) telah berusia 21 tahun ke atas dan memenuhi ketentuan UU No. 16 Tahun 2019."
        }

        return CoupleAgeResult(
            weddingDateIso = weddingDateIso,
            groom = groom,
            bride = bride,
            overallTier = overallTier,
            overallTitle = overallTitle,
            overallSummary = overallSummary
        )
    }
}
