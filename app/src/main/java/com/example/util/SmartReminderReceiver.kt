package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.KuaLocalDatabase
import com.example.data.KuaSessionManager
import com.example.data.MarriageSchedule
import com.example.data.ReminderSettings
import com.example.data.UserRole
import com.example.data.WhatsAppAppPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmartReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val isReminderTrigger = action == BackgroundReminderScheduler.ACTION_DAILY_SMART_REMINDER
        val isRescheduleTrigger = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED

        if (!isReminderTrigger && !isRescheduleTrigger) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = KuaLocalDatabase.getInstance(appContext).kuaDao()
                val settingsEntity = dao.getReminderSettingsOnce()
                val settings = if (settingsEntity == null) {
                    ReminderSettings()
                } else {
                    ReminderSettings(
                        id = settingsEntity.id,
                        autoPhoneNotifEnabled = settingsEntity.autoPhoneNotifEnabled,
                        whatsappReminderEnabled = settingsEntity.whatsappReminderEnabled,
                        remindOnWeddingDay = settingsEntity.remindOnWeddingDay,
                        remindOneDayBefore = settingsEntity.remindOneDayBefore,
                        remindThreeDaysBefore = settingsEntity.remindThreeDaysBefore,
                        reminderTime = settingsEntity.reminderTime,
                        adminWhatsappNumber = settingsEntity.adminWhatsappNumber,
                        adminWhatsappApp = settingsEntity.adminWhatsappApp.ifBlank { WhatsAppAppPreference.AUTO.code },
                        whatsappTemplate = settingsEntity.whatsappTemplate.ifBlank { ReminderSettings.DEFAULT_WA_TEMPLATE },
                        whatsappPenghuluTemplate = settingsEntity.whatsappPenghuluTemplate
                            .ifBlank { ReminderSettings.DEFAULT_WA_PENGHULU_TEMPLATE },
                        headOfKuaTitle = settingsEntity.headOfKuaTitle.ifBlank { ReminderSettings.DEFAULT_HEAD_OF_KUA_TITLE },
                        headOfKuaName = settingsEntity.headOfKuaName,
                        headOfKuaNip = settingsEntity.headOfKuaNip,
                        defaultWaliHakimAccountId = settingsEntity.defaultWaliHakimAccountId,
                        defaultWaliHakimName = settingsEntity.defaultWaliHakimName,
                        updatedByName = settingsEntity.updatedByName,
                        updatedAt = settingsEntity.updatedAt
                    )
                }

                if (isReminderTrigger && settings.autoPhoneNotifEnabled) {
                    val allSchedules = dao.getAllSchedulesOnce().map { entity ->
                        MarriageSchedule(
                            id = entity.id,
                            registrationNumber = entity.registrationNumber,
                            groomName = entity.groomName,
                            groomNik = entity.groomNik,
                            brideName = entity.brideName,
                            brideNik = entity.brideNik,
                            villageId = entity.villageId,
                            villageName = entity.villageName,
                            modinAccountId = entity.modinAccountId,
                            modinName = entity.modinName,
                            weddingDate = entity.weddingDate,
                            weddingTime = entity.weddingTime,
                            locationType = entity.locationType,
                            locationAddress = entity.locationAddress,
                            penghuluAccountId = entity.penghuluAccountId,
                            penghuluName = entity.penghuluName,
                            waliStatus = entity.waliStatus,
                            waliRelationship = entity.waliRelationship,
                            waliName = entity.waliName,
                            waliBin = entity.waliBin,
                            waliNik = entity.waliNik,
                            waliAddress = entity.waliAddress,
                            waliHakimAccountId = entity.waliHakimAccountId,
                            waliHakimReason = entity.waliHakimReason,
                            validationStatus = entity.validationStatus,
                            documentsComplete = entity.documentsComplete,
                            documentChecklistCodes = entity.documentChecklistCodes,
                            verificationNote = entity.verificationNote,
                            verifiedByName = entity.verifiedByName,
                            createdAt = entity.createdAt,
                            updatedAt = entity.updatedAt
                        )
                    }

                    val session = KuaSessionManager(appContext).getSavedSession()
                    val activeStaff = session?.let { dao.getStaffAccountById(it.staffAccountId) }
                    val filteredSchedules = when (UserRole.fromCode(activeStaff?.role)) {
                        UserRole.MODIN -> if (activeStaff != null) {
                            allSchedules.filter { s ->
                                s.modinAccountId == activeStaff.id ||
                                    (s.modinAccountId.isBlank() && s.villageId == activeStaff.villageId)
                            }
                        } else {
                            allSchedules
                        }
                        UserRole.PENGHULU -> if (activeStaff != null) {
                            allSchedules.filter { s ->
                                s.penghuluAccountId == activeStaff.id ||
                                    s.waliHakimAccountId == activeStaff.id ||
                                    (s.penghuluAccountId.isBlank() &&
                                        s.penghuluName.equals(activeStaff.officerName, ignoreCase = true))
                            }
                        } else {
                            allSchedules
                        }
                        UserRole.ADMIN -> allSchedules
                    }

                    SmartReminderManager.checkAndTriggerAutoNotifications(
                        context = appContext,
                        schedules = filteredSchedules,
                        settings = settings
                    )
                }

                // Always reschedule for the next day (or after boot/time change)
                BackgroundReminderScheduler.scheduleDailyReminder(appContext, settings)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
