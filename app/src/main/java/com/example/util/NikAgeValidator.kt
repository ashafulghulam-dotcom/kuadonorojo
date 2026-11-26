package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class NikAgeAnalysis(
    val nik: String,
    val isCompleteNik: Boolean,
    val isValidDate: Boolean,
    val birthDateIso: String? = null,
    val birthDateFormatted: String? = null,
    val genderLabel: String? = null,
    val genderMismatchWarning: String? = null,
    val ageYears: Int? = null,
    val ageMonths: Int? = null,
    val requiresDispensasiKawin: Boolean = false,
    val errorMessage: String? = null
) {
    fun formattedAge(): String {
        val y = ageYears ?: return "-"
        val m = ageMonths ?: 0
        return if (m > 0) "$y tahun $m bulan" else "$y tahun"
    }
}

data class CoupleAgeCheck(
    val groom: NikAgeAnalysis?,
    val bride: NikAgeAnalysis?
) {
    val requiresDispensasiKawin: Boolean
        get() = (groom?.requiresDispensasiKawin == true) || (bride?.requiresDispensasiKawin == true)

    fun underageDetails(): List<String> = buildList {
        if (groom?.requiresDispensasiKawin == true) {
            add("Calon Suami berusia ${groom.formattedAge()} saat tanggal akad (lahir ${groom.birthDateFormatted ?: "-"})")
        }
        if (bride?.requiresDispensasiKawin == true) {
            add("Calon Istri berusia ${bride.formattedAge()} saat tanggal akad (lahir ${bride.birthDateFormatted ?: "-"})")
        }
    }

    fun recommendedNoteText(): String {
        val details = underageDetails().joinToString("; ")
        return if (details.isNotBlank()) {
            "Wajib Lampirkan Dispensasi Kawin PA (UU No. 16 Tahun 2019 — batas minimal 19 tahun): $details."
        } else {
            "Wajib Lampirkan Dispensasi Kawin PA sesuai UU No. 16 Tahun 2019."
        }
    }
}

object NikAgeValidator {
    const val MINIMUM_MARRIAGE_AGE_YEARS = 19

    /**
     * Parses a 16-digit Indonesian NIK (PPKKCCDDMMYYNNNN) and calculates the person's exact age
     * on [weddingDateIso] (yyyy-MM-dd) according to UU No. 16 Tahun 2019 (minimum age 19 years).
     *
     * Per Indonesian Dukcapil NIK specification:
     * - Digits 7..8 (indices 6..7): Day of birth (01..31 for Male, 41..71 for Female = day + 40).
     * - Digits 9..10 (indices 8..9): Month of birth (01..12).
     * - Digits 11..12 (indices 10..11): Last 2 digits of birth year (00..99).
     */
    fun analyzeNikAtDate(
        rawNik: String,
        weddingDateIso: String,
        expectedFemale: Boolean? = null
    ): NikAgeAnalysis? {
        val digits = rawNik.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        if (digits.length < 16) {
            return NikAgeAnalysis(
                nik = digits,
                isCompleteNik = false,
                isValidDate = false
            )
        }

        val nik16 = digits.take(16)
        val rawDay = nik16.substring(6, 8).toIntOrNull()
        val month = nik16.substring(8, 10).toIntOrNull()
        val yy = nik16.substring(10, 12).toIntOrNull()

        if (rawDay == null || month == null || yy == null) {
            return NikAgeAnalysis(
                nik = nik16,
                isCompleteNik = true,
                isValidDate = false,
                errorMessage = "Format tanggal lahir pada NIK tidak terbaca."
            )
        }

        val isFemaleFromNik: Boolean
        val day: Int
        when (rawDay) {
            in 1..31 -> {
                isFemaleFromNik = false
                day = rawDay
            }
            in 41..71 -> {
                isFemaleFromNik = true
                day = rawDay - 40
            }
            else -> {
                return NikAgeAnalysis(
                    nik = nik16,
                    isCompleteNik = true,
                    isValidDate = false,
                    errorMessage = "Kode tanggal lahir NIK ($rawDay) tidak valid. Periksa kembali digit ke-7 s.d. ke-8."
                )
            }
        }

        if (month !in 1..12) {
            return NikAgeAnalysis(
                nik = nik16,
                isCompleteNik = true,
                isValidDate = false,
                errorMessage = "Kode bulan lahir NIK ($month) tidak valid. Periksa kembali digit ke-9 s.d. ke-10."
            )
        }

        val referenceCal = parseIsoCalendar(weddingDateIso) ?: Calendar.getInstance()
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val currentTwoDigit = currentYear % 100
        val birthYear = if (yy <= currentTwoDigit) 2000 + yy else 1900 + yy

        // Validate real calendar date (e.g., rejects Feb 30, Apr 31)
        val birthCal = Calendar.getInstance().apply {
            isLenient = false
            clear()
            set(Calendar.YEAR, birthYear)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
        }
        try {
            birthCal.timeInMillis // Triggers strict calendar validation
        } catch (_: Exception) {
            return NikAgeAnalysis(
                nik = nik16,
                isCompleteNik = true,
                isValidDate = false,
                errorMessage = "Tanggal lahir ($day-$month-$birthYear) pada NIK tidak valid."
            )
        }

        val refYear = referenceCal.get(Calendar.YEAR)
        val refMonth = referenceCal.get(Calendar.MONTH) + 1
        val refDay = referenceCal.get(Calendar.DAY_OF_MONTH)

        var years = refYear - birthYear
        var months = refMonth - month
        if (refDay < day) {
            months -= 1
        }
        if (months < 0) {
            years -= 1
            months += 12
        }

        if (years < 0 || years > 120) {
            return NikAgeAnalysis(
                nik = nik16,
                isCompleteNik = true,
                isValidDate = false,
                errorMessage = "Tanggal lahir pada NIK melampaui tanggal akad. Mohon periksa kembali NIK."
            )
        }

        val isoFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val displayFormatter = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
        val genderLabel = if (isFemaleFromNik) "Perempuan" else "Laki-laki"
        val genderMismatch = when {
            expectedFemale == false && isFemaleFromNik ->
                "Kode NIK terbaca Perempuan (tgl +40). Pastikan NIK Calon Suami tidak tertukar."
            expectedFemale == true && !isFemaleFromNik ->
                "Kode NIK terbaca Laki-laki. Pastikan NIK Calon Istri tidak tertukar."
            else -> null
        }

        return NikAgeAnalysis(
            nik = nik16,
            isCompleteNik = true,
            isValidDate = true,
            birthDateIso = isoFormatter.format(birthCal.time),
            birthDateFormatted = displayFormatter.format(birthCal.time),
            genderLabel = genderLabel,
            genderMismatchWarning = genderMismatch,
            ageYears = years,
            ageMonths = months,
            requiresDispensasiKawin = years < MINIMUM_MARRIAGE_AGE_YEARS
        )
    }

    fun analyzeCouple(
        groomNik: String,
        brideNik: String,
        weddingDateIso: String
    ): CoupleAgeCheck {
        return CoupleAgeCheck(
            groom = analyzeNikAtDate(groomNik, weddingDateIso, expectedFemale = false),
            bride = analyzeNikAtDate(brideNik, weddingDateIso, expectedFemale = true)
        )
    }

    private fun parseIsoCalendar(isoDate: String): Calendar? {
        return try {
            val parts = isoDate.trim().split("-")
            if (parts.size != 3) return null
            val y = parts[0].toInt()
            val m = parts[1].toInt()
            val d = parts[2].toInt()
            Calendar.getInstance().apply {
                isLenient = false
                clear()
                set(Calendar.YEAR, y)
                set(Calendar.MONTH, m - 1)
                set(Calendar.DAY_OF_MONTH, d)
                timeInMillis
            }
        } catch (_: Exception) {
            null
        }
    }
}
