package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.MarriageSchedule
import com.example.data.ReminderSettings
import com.example.data.StaffAccount
import com.example.data.UserRole
import com.example.data.ValidationStatus
import com.example.data.daysUntilWedding
import com.example.data.formatIndonesianDate
import com.example.data.todayIsoDate
import java.net.URLEncoder

object SmartReminderManager {
    private const val CHANNEL_ID = "kua_donorojo_jadwal_nikah"
    private const val CHANNEL_NAME = "Pengingat Jadwal Nikah KUA Donorojo"
    private const val CHANNEL_DESC = "Notifikasi otomatis jadwal akad nikah hari H dan pengingat berkas untuk Modin & Admin KUA Donorojo."
    private const val PREFS_NAME = "kua_donorojo_reminder_prefs"
    private const val KEY_NOTIFIED_SET = "notified_schedule_keys"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun sendScheduleNotification(
        context: Context,
        schedule: MarriageSchedule,
        prefixLabel: String = "Jadwal Akad Hari Ini"
    ): Boolean {
        ensureNotificationChannel(context)
        if (!hasNotificationPermission(context)) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            schedule.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_logo_kua_resmi)
        } catch (_: Exception) {
            null
        }

        val statusLabel = schedule.resolvedStatus().label
        val title = "$prefixLabel • ${schedule.villageName}"
        val shortText = "${schedule.groomName} & ${schedule.brideName} (${schedule.weddingTime} WIB)"
        val expandedText = buildString {
            append("Pengantin: ${schedule.groomName} & ${schedule.brideName}\n")
            append("Tanggal: ${formatIndonesianDate(schedule.weddingDate)} • ${schedule.weddingTime} WIB\n")
            append("Lokasi: ${schedule.locationAddress}\n")
            if (schedule.modinName.isNotBlank()) {
                append("Modin: ${schedule.modinName}\n")
            }
            if (schedule.penghuluName.isNotBlank()) {
                append("Penghulu: ${schedule.penghuluName}\n")
            }
            append("Status KUA: $statusLabel")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo_kua_resmi)
            .setContentTitle(title)
            .setContentText(shortText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }

        return try {
            val notifId = (schedule.id.ifBlank { schedule.registrationNumber }).hashCode()
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun checkAndTriggerAutoNotifications(
        context: Context,
        schedules: List<MarriageSchedule>,
        settings: ReminderSettings
    ): Int {
        if (!settings.autoPhoneNotifEnabled) return 0
        if (!hasNotificationPermission(context)) return 0

        val today = todayIsoDate()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingSet = prefs.getStringSet(KEY_NOTIFIED_SET, emptySet())?.toMutableSet() ?: mutableSetOf()
        var triggeredCount = 0

        for (schedule in schedules) {
            val status = schedule.resolvedStatus()
            if (status == ValidationStatus.DIBATALKAN || status == ValidationStatus.SELESAI) continue

            val diffDays = daysUntilWedding(schedule.weddingDate) ?: continue
            val prefixLabel = when {
                diffDays == 0 && settings.remindOnWeddingDay -> "Jadwal Akad Hari Ini (H-0)"
                diffDays == 1 && settings.remindOneDayBefore -> "Pengingat Akad Besok (H-1)"
                diffDays == 3 && settings.remindThreeDaysBefore -> "Pengingat Akad 3 Hari Lagi (H-3)"
                else -> null
            } ?: continue

            val key = "${today}_${schedule.id}_${diffDays}_${status.code}"
            if (key !in existingSet) {
                val sent = sendScheduleNotification(context, schedule, prefixLabel)
                if (sent) {
                    existingSet.add(key)
                    triggeredCount++
                }
            }
        }

        if (triggeredCount > 0) {
            prefs.edit().putStringSet(KEY_NOTIFIED_SET, existingSet).apply()
        }
        return triggeredCount
    }

    enum class ReminderRecipientType(val label: String) {
        MODIN("Modin Desa"),
        PENGHULU("Penghulu KUA")
    }

    fun resolveAssignedModin(
        schedule: MarriageSchedule,
        staffAccounts: List<StaffAccount>
    ): StaffAccount? {
        val modins = staffAccounts.filter { it.resolvedRole() == UserRole.MODIN }
        if (schedule.modinAccountId.isNotBlank()) {
            modins.firstOrNull { it.id == schedule.modinAccountId }?.let { return it }
        }
        if (schedule.modinName.isNotBlank()) {
            modins.firstOrNull {
                it.villageId == schedule.villageId &&
                    it.officerName.equals(schedule.modinName, ignoreCase = true)
            }?.let { return it }
        }
        return modins.firstOrNull { it.villageId == schedule.villageId && it.active }
            ?: modins.firstOrNull { it.villageId == schedule.villageId }
    }

    fun resolveAssignedPenghulu(
        schedule: MarriageSchedule,
        staffAccounts: List<StaffAccount>
    ): StaffAccount? {
        val penghulus = staffAccounts.filter { it.resolvedRole() == UserRole.PENGHULU }
        if (schedule.penghuluAccountId.isNotBlank()) {
            penghulus.firstOrNull { it.id == schedule.penghuluAccountId }?.let { return it }
        }
        if (schedule.penghuluName.isNotBlank()) {
            penghulus.firstOrNull {
                it.officerName.equals(schedule.penghuluName, ignoreCase = true)
            }?.let { return it }
        }
        if (schedule.waliHakimAccountId.isNotBlank()) {
            penghulus.firstOrNull { it.id == schedule.waliHakimAccountId }?.let { return it }
        }
        return null
    }

    fun resolveAdminWhatsappNumber(
        settings: ReminderSettings,
        staffAccounts: List<StaffAccount>
    ): String {
        val fromSettings = settings.adminWhatsappNumber.trim()
        if (fromSettings.isNotBlank()) return fromSettings
        return staffAccounts
            .firstOrNull { it.resolvedRole() == UserRole.ADMIN }
            ?.whatsappNumber
            ?.trim()
            .orEmpty()
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun formatWhatsAppMessage(
        template: String,
        schedule: MarriageSchedule,
        recipientType: ReminderRecipientType = ReminderRecipientType.MODIN,
        adminWhatsappNumber: String = ""
    ): String {
        val defaultTemplate = when (recipientType) {
            ReminderRecipientType.MODIN -> ReminderSettings.DEFAULT_WA_TEMPLATE
            ReminderRecipientType.PENGHULU -> ReminderSettings.DEFAULT_WA_PENGHULU_TEMPLATE
        }
        val base = template.ifBlank { defaultTemplate }
        val modinDisplay = schedule.modinName.ifBlank { "Modin ${schedule.villageName}" }
        val penghuluDisplay = schedule.penghuluName.ifBlank { "Penghulu KUA Donorojo" }
        val waliDisplay = schedule.waliSummaryText().ifBlank { schedule.resolvedWaliStatus().label }
        val cleanAdminWa = adminWhatsappNumber.trim()
        val adminWaDisplay = cleanAdminWa.ifBlank { "KUA Donorojo" }

        val formatted = base
            .replace("{pengantin}", "${schedule.groomName} & ${schedule.brideName}")
            .replace("{desa}", schedule.villageName)
            .replace("{modin}", modinDisplay)
            .replace("{tanggal}", formatIndonesianDate(schedule.weddingDate))
            .replace("{jam}", schedule.weddingTime)
            .replace("{lokasi}", schedule.locationAddress)
            .replace("{penghulu}", penghuluDisplay)
            .replace("{wali}", waliDisplay)
            .replace("{status}", schedule.resolvedStatus().label)
            .replace("{no_daftar}", schedule.registrationNumber)
            .replace("{wa_admin}", adminWaDisplay)

        return if (cleanAdminWa.isNotBlank() && !formatted.contains(cleanAdminWa)) {
            "$formatted\n\nDikirim via WA Resmi Admin KUA Donorojo: $cleanAdminWa"
        } else {
            formatted
        }
    }

    fun normalizeIndonesianPhone(rawPhone: String): String {
        val digits = rawPhone.filter { it.isDigit() }
        return when {
            digits.isBlank() -> ""
            digits.startsWith("0") -> "62" + digits.drop(1)
            digits.startsWith("62") -> digits
            else -> "62$digits"
        }
    }

    fun launchWhatsAppReminder(
        context: Context,
        rawPhone: String,
        message: String,
        appPreference: com.example.data.WhatsAppAppPreference = com.example.data.WhatsAppAppPreference.AUTO
    ): String {
        val normalizedPhone = normalizeIndonesianPhone(rawPhone)
        val encodedText = URLEncoder.encode(message, "UTF-8")

        val waUrl = if (normalizedPhone.isNotBlank()) {
            "https://wa.me/$normalizedPhone?text=$encodedText"
        } else {
            "https://wa.me/?text=$encodedText"
        }

        val preferredPkg = appPreference.packageName
        if (preferredPkg != null && isPackageInstalled(context, preferredPkg)) {
            val targetedIntent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)).apply {
                setPackage(preferredPkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(targetedIntent)
                return appPreference.label
            } catch (_: Exception) {
                // Fallback below
            }
        }

        val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(waIntent)
            return "WhatsApp"
        } catch (_: Exception) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Kirim Pengingat via WhatsApp / Pesan"))
            return "Bagikan Pesan"
        }
    }
}
