package com.example.util

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.data.MarriageSchedule
import com.example.data.ValidationStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class DeviceCalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String
)

sealed interface CalendarSyncResult {
    data class Success(
        val calendarName: String,
        val inserted: Int,
        val updated: Int,
        val removed: Int,
        val totalActive: Int
    ) : CalendarSyncResult

    data object NoWritableCalendar : CalendarSyncResult
    data class Error(val message: String) : CalendarSyncResult
}

object DeviceCalendarSyncManager {
    private const val PREFS_NAME = "kua_donorojo_calendar_sync"
    private const val KEY_AUTO_SYNC_PREFIX = "auto_sync_"
    private const val KEY_LAST_SYNC_PREFIX = "last_sync_at_"
    private const val KEY_LAST_CAL_NAME_PREFIX = "last_cal_name_"
    private const val EVENT_DURATION_MILLIS = 60L * 60L * 1000L // 1 hour

    val REQUIRED_PERMISSIONS: Array<String> = arrayOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )

    fun hasCalendarPermissions(context: Context): Boolean {
        val readOk = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val writeOk = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        return readOk && writeOk
    }

    fun isAutoSyncEnabled(context: Context, accountId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("$KEY_AUTO_SYNC_PREFIX$accountId", false)
    }

    fun setAutoSyncEnabled(context: Context, accountId: String, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("$KEY_AUTO_SYNC_PREFIX$accountId", enabled).apply()
    }

    fun getLastSyncTimeMillis(context: Context, accountId: String): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("$KEY_LAST_SYNC_PREFIX$accountId", 0L)
    }

    fun getLastCalendarName(context: Context, accountId: String): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("$KEY_LAST_CAL_NAME_PREFIX$accountId", "").orEmpty()
    }

    private fun saveSyncMetadata(context: Context, accountId: String, calendarName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("$KEY_LAST_SYNC_PREFIX$accountId", System.currentTimeMillis())
            .putString("$KEY_LAST_CAL_NAME_PREFIX$accountId", calendarName)
            .apply()
    }

    fun findPrimaryWritableCalendar(context: Context): DeviceCalendarInfo? {
        if (!hasCalendarPermissions(context)) return null
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val selectionArgs = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

        val candidates = mutableListOf<Triple<DeviceCalendarInfo, Int, Int>>()
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val nameIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                val primaryIdx = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                val visibleIdx = cursor.getColumnIndex(CalendarContract.Calendars.VISIBLE)

                while (cursor.moveToNext()) {
                    if (idIdx < 0) continue
                    val id = cursor.getLong(idIdx)
                    val displayName = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else "Kalender HP"
                    val accountName = if (accIdx >= 0) cursor.getString(accIdx).orEmpty() else ""
                    val isPrimary = if (primaryIdx >= 0) cursor.getInt(primaryIdx) else 0
                    val isVisible = if (visibleIdx >= 0) cursor.getInt(visibleIdx) else 1
                    candidates.add(
                        Triple(
                            DeviceCalendarInfo(
                                id = id,
                                displayName = displayName.ifBlank { accountName.ifBlank { "Kalender HP" } },
                                accountName = accountName
                            ),
                            isPrimary,
                            isVisible
                        )
                    )
                }
            }
        } catch (_: Exception) {
            return null
        }

        if (candidates.isEmpty()) return null
        return candidates
            .sortedWith(
                compareByDescending<Triple<DeviceCalendarInfo, Int, Int>> { it.second }
                    .thenByDescending { it.third }
                    .thenByDescending { it.first.accountName.contains("@", ignoreCase = true) }
            )
            .first()
            .first
    }

    fun syncSchedulesToDeviceCalendar(
        context: Context,
        schedules: List<MarriageSchedule>,
        roleLabel: String,
        accountId: String
    ): CalendarSyncResult {
        if (!hasCalendarPermissions(context)) {
            return CalendarSyncResult.Error("Izin akses Kalender HP belum diaktifkan.")
        }

        val targetCalendar = findPrimaryWritableCalendar(context)
            ?: return CalendarSyncResult.NoWritableCalendar

        return try {
            val resolver = context.contentResolver
            val tzId = TimeZone.getDefault().id
            var inserted = 0
            var updated = 0
            var removed = 0
            var totalActive = 0

            schedules.forEach { schedule ->
                val syncTag = buildSyncTag(accountId, schedule.id)
                val existingEventId = findExistingEventId(context, targetCalendar.id, syncTag)
                val isCancelled = schedule.resolvedStatus() == ValidationStatus.DIBATALKAN

                if (isCancelled) {
                    if (existingEventId != null) {
                        val deleteUri = ContentUris.withAppendedId(
                            CalendarContract.Events.CONTENT_URI,
                            existingEventId
                        )
                        resolver.delete(deleteUri, null, null)
                        removed++
                    }
                    return@forEach
                }

                val startMillis = parseWeddingStartMillis(schedule.weddingDate, schedule.weddingTime)
                    ?: return@forEach
                val endMillis = startMillis + EVENT_DURATION_MILLIS
                totalActive++

                val title = buildEventTitle(schedule)
                val location = buildEventLocation(schedule)
                val description = buildEventDescription(schedule, roleLabel, syncTag)

                val values = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, targetCalendar.id)
                    put(CalendarContract.Events.TITLE, title)
                    put(CalendarContract.Events.DESCRIPTION, description)
                    put(CalendarContract.Events.EVENT_LOCATION, location)
                    put(CalendarContract.Events.DTSTART, startMillis)
                    put(CalendarContract.Events.DTEND, endMillis)
                    put(CalendarContract.Events.EVENT_TIMEZONE, tzId)
                    put(CalendarContract.Events.HAS_ALARM, 1)
                }

                if (existingEventId != null) {
                    val updateUri = ContentUris.withAppendedId(
                        CalendarContract.Events.CONTENT_URI,
                        existingEventId
                    )
                    resolver.update(updateUri, values, null, null)
                    updated++
                } else {
                    val createdUri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
                    val newEventId = createdUri?.lastPathSegment?.toLongOrNull()
                    if (newEventId != null) {
                        addEventReminder(context, newEventId, 60) // 1 jam sebelum akad
                        addEventReminder(context, newEventId, 1440) // H-1 sebelum akad
                    }
                    inserted++
                }
            }

            saveSyncMetadata(context, accountId, targetCalendar.displayName)
            CalendarSyncResult.Success(
                calendarName = targetCalendar.displayName,
                inserted = inserted,
                updated = updated,
                removed = removed,
                totalActive = totalActive
            )
        } catch (e: Exception) {
            CalendarSyncResult.Error(e.localizedMessage ?: "Gagal menautkan jadwal ke Kalender HP.")
        }
    }

    fun openSingleScheduleInCalendarIntent(
        context: Context,
        schedule: MarriageSchedule,
        roleLabel: String
    ): Boolean {
        val startMillis = parseWeddingStartMillis(schedule.weddingDate, schedule.weddingTime)
            ?: System.currentTimeMillis()
        val endMillis = startMillis + EVENT_DURATION_MILLIS
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
            putExtra(CalendarContract.Events.TITLE, buildEventTitle(schedule))
            putExtra(CalendarContract.Events.EVENT_LOCATION, buildEventLocation(schedule))
            putExtra(
                CalendarContract.Events.DESCRIPTION,
                buildEventDescription(schedule, roleLabel, "")
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openDeviceCalendarApp(context: Context): Boolean {
        return try {
            val builder = CalendarContract.CONTENT_URI.buildUpon()
            builder.appendPath("time")
            ContentUris.appendId(builder, System.currentTimeMillis())
            val intent = Intent(Intent.ACTION_VIEW).setData(builder.build()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun findExistingEventId(
        context: Context,
        calendarId: Long,
        syncTag: String
    ): Long? {
        val projection = arrayOf(CalendarContract.Events._ID)
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DESCRIPTION} LIKE ? AND ${CalendarContract.Events.DELETED} != 1"
        val selectionArgs = arrayOf(calendarId.toString(), "%$syncTag%")
        return try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun addEventReminder(context: Context, eventId: Long, minutesBefore: Int) {
        try {
            val reminderValues = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, minutesBefore)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
        } catch (_: Exception) {
            // Optional reminder failure should not fail event sync
        }
    }

    private fun buildSyncTag(accountId: String, scheduleId: String): String =
        "[KUA-DONOROJO:$accountId:$scheduleId]"

    private fun buildEventTitle(schedule: MarriageSchedule): String {
        val villageShort = schedule.villageName.removePrefix("Desa ").trim()
        val suffix = if (villageShort.isNotBlank()) " ($villageShort)" else ""
        return "Akad Nikah: ${schedule.groomName} & ${schedule.brideName}$suffix"
    }

    private fun buildEventLocation(schedule: MarriageSchedule): String {
        val parts = listOf(
            schedule.resolvedLocationType().label,
            schedule.locationAddress.trim(),
            schedule.villageName.trim()
        ).filter { it.isNotBlank() }
        return parts.joinToString(" • ")
    }

    private fun buildEventDescription(
        schedule: MarriageSchedule,
        roleLabel: String,
        syncTag: String
    ): String {
        return buildString {
            appendLine("Jadwal Resmi KUA Donorojo ($roleLabel)")
            appendLine("No. Daftar: ${schedule.registrationNumber}")
            appendLine("Pengantin: ${schedule.groomName} & ${schedule.brideName}")
            appendLine("Wilayah: ${schedule.villageName}")
            if (schedule.modinName.isNotBlank()) {
                appendLine("Modin: ${schedule.modinName}")
            }
            if (schedule.penghuluName.isNotBlank()) {
                appendLine("Penghulu: ${schedule.penghuluName}")
            }
            val waliSummary = schedule.waliSummaryText()
            if (waliSummary.isNotBlank()) {
                appendLine("Wali Nikah: $waliSummary")
            }
            appendLine("Status KUA: ${schedule.resolvedStatus().label}")
            if (schedule.verificationNote.isNotBlank()) {
                appendLine("Catatan: ${schedule.verificationNote}")
            }
            if (syncTag.isNotBlank()) {
                appendLine()
                append(syncTag)
            }
        }.trim()
    }

    private fun parseWeddingStartMillis(isoDate: String, timeHm: String): Long? {
        return try {
            val dateParts = isoDate.trim().split("-")
            if (dateParts.size != 3) return null
            val year = dateParts[0].toInt()
            val month = dateParts[1].toInt() - 1
            val day = dateParts[2].toInt()

            val timeParts = timeHm.trim().split(":")
            val hour = timeParts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
            val minute = timeParts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0

            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, day)
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } catch (_: Exception) {
            try {
                val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                parser.parse(isoDate)?.time
            } catch (_: Exception) {
                null
            }
        }
    }
}
