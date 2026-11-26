package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.example.data.LocationType
import com.example.data.MarriageSchedule
import com.example.data.ValidationStatus
import com.example.data.WhatsAppAppPreference
import com.example.data.formatIndonesianDate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class DigestPeriod(
    val shortLabel: String,
    val titleLabel: String
) {
    TODAY("Hari Ini", "HARI INI"),
    TOMORROW("Besok", "BESOK"),
    THIS_WEEK("Minggu Ini", "MINGGU INI (7 HARI)")
}

object WaScheduleDigestFormatter {

    private fun isoDateWithOffset(dayOffset: Int): String {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
        }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    fun dateRangeFor(period: DigestPeriod): Pair<String, String> = when (period) {
        DigestPeriod.TODAY -> {
            val today = isoDateWithOffset(0)
            today to today
        }
        DigestPeriod.TOMORROW -> {
            val tomorrow = isoDateWithOffset(1)
            tomorrow to tomorrow
        }
        DigestPeriod.THIS_WEEK -> {
            isoDateWithOffset(0) to isoDateWithOffset(6)
        }
    }

    fun filterSchedulesForPeriod(
        schedules: List<MarriageSchedule>,
        period: DigestPeriod,
        villageCodeFilter: String? = null,
        includeCancelled: Boolean = false
    ): List<MarriageSchedule> {
        val (startIso, endIso) = dateRangeFor(period)
        return schedules
            .filter { item ->
                val inRange = item.weddingDate >= startIso && item.weddingDate <= endIso
                val matchesVillage = villageCodeFilter.isNullOrBlank() || item.villageId == villageCodeFilter
                val matchesStatus = includeCancelled || item.resolvedStatus() != ValidationStatus.DIBATALKAN
                inRange && matchesVillage && matchesStatus
            }
            .sortedWith(
                compareBy<MarriageSchedule> { it.weddingDate }
                    .thenBy { it.weddingTime }
                    .thenBy { it.registrationNumber }
            )
    }

    fun formatWhatsAppDigest(
        schedules: List<MarriageSchedule>,
        period: DigestPeriod,
        scopeLabel: String = "Semua Wilayah (8 Desa)",
        adminWhatsappNumber: String = "",
        villageCodeFilter: String? = null
    ): String {
        val (startIso, endIso) = dateRangeFor(period)
        val filtered = filterSchedulesForPeriod(
            schedules = schedules,
            period = period,
            villageCodeFilter = villageCodeFilter,
            includeCancelled = false
        )

        val periodDateLabel = if (startIso == endIso) {
            formatIndonesianDate(startIso)
        } else {
            "${formatIndonesianDate(startIso)} s.d. ${formatIndonesianDate(endIso)}"
        }

        val kuaCount = filtered.count { it.resolvedLocationType() == LocationType.KUA }
        val outsideCount = filtered.count { it.resolvedLocationType() == LocationType.LUAR_KUA }

        return buildString {
            append("*REKAP JADWAL AKAD NIKAH — ${period.titleLabel}*\n")
            append("*KUA DONOROJO • KABUPATEN JEPARA*\n")
            append("Periode : $periodDateLabel\n")
            append("Lingkup : $scopeLabel\n")
            append("Total   : ${filtered.size} Jadwal")
            if (filtered.isNotEmpty()) {
                append(" ($kuaCount Balai KUA • $outsideCount Luar KUA)")
            }
            append("\n")
            append("━━━━━━━━━━━━━━━━━━━━\n")

            if (filtered.isEmpty()) {
                append("\n_Belum ada jadwal akad nikah aktif pada periode ini._\n")
            } else if (period == DigestPeriod.THIS_WEEK) {
                val groupedByDate = filtered.groupBy { it.weddingDate }
                var globalIndex = 1
                groupedByDate.forEach { (isoDate, daySchedules) ->
                    append("\n*${formatIndonesianDate(isoDate).uppercase(Locale("id", "ID"))}* (${daySchedules.size} jadwal)\n")
                    daySchedules.forEach { item ->
                        appendScheduleEntry(globalIndex, item)
                        globalIndex++
                    }
                }
            } else {
                append("\n")
                filtered.forEachIndexed { idx, item ->
                    appendScheduleEntry(idx + 1, item)
                }
            }

            append("━━━━━━━━━━━━━━━━━━━━\n")
            append("_Mohon koordinasi Penghulu & Modin terkait kelengkapan berkas dan ketepatan waktu pelaksanaan._\n")
            append("_KUA Donorojo • Kementerian Agama Kabupaten Jepara_")
            val cleanAdminWa = adminWhatsappNumber.trim()
            if (cleanAdminWa.isNotBlank()) {
                append("\n_Info Admin KUA: ${cleanAdminWa}_")
            }
        }
    }

    private fun StringBuilder.appendScheduleEntry(index: Int, item: MarriageSchedule) {
        val penghuluDisplay = item.penghuluName.ifBlank { "Belum ditentukan" }
        val modinDisplay = item.modinName.ifBlank { "Belum ditentukan" }
        val locationLabel = item.resolvedLocationType().label
        val addressDisplay = item.locationAddress.ifBlank { locationLabel }
        val locationSummary = if (addressDisplay.equals(locationLabel, ignoreCase = true)) {
            addressDisplay
        } else {
            "$addressDisplay ($locationLabel)"
        }
        val waliSummary = item.waliSummaryText().ifBlank { item.resolvedWaliStatus().label }
        val statusLabel = item.resolvedStatus().label

        append("*$index. ${item.weddingTime} WIB — ${item.groomName} & ${item.brideName}*\n")
        append("   • Desa     : ${item.villageName}\n")
        append("   • Lokasi   : $locationSummary\n")
        append("   • Penghulu : $penghuluDisplay\n")
        append("   • Modin    : $modinDisplay\n")
        append("   • Wali     : $waliSummary\n")
        append("   • Status   : $statusLabel\n\n")
    }

    fun copyToClipboard(
        context: Context,
        text: String,
        label: String = "Rekap Jadwal KUA Donorojo"
    ): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return false
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun shareToWhatsAppGroup(
        context: Context,
        message: String,
        appPreference: WhatsAppAppPreference = WhatsAppAppPreference.AUTO
    ): String {
        val candidatePackages = buildList {
            appPreference.packageName?.let { add(it) }
            if ("com.whatsapp" !in this) add("com.whatsapp")
            if ("com.whatsapp.w4b" !in this) add("com.whatsapp.w4b")
        }

        for (pkg in candidatePackages) {
            if (SmartReminderManager.isPackageInstalled(context, pkg)) {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    setPackage(pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(sendIntent)
                    return if (pkg == "com.whatsapp.w4b") "WhatsApp Business" else "WhatsApp"
                } catch (_: Exception) {
                    // Try next candidate
                }
            }
        }

        return shareViaSystemChooser(context, message)
    }

    fun shareViaSystemChooser(
        context: Context,
        message: String
    ): String {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(shareIntent, "Bagikan Rekap Jadwal KUA Donorojo").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        return "Bagikan Pesan"
    }
}
