package com.example.util

import android.content.Context
import android.net.Uri
import com.example.data.MarriageSchedule
import com.example.data.ReminderSettings
import com.example.data.StaffAccount
import com.example.data.Village
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class KuaBackupPayload(
    val exportedAtMillis: Long,
    val appVersionTag: String,
    val villages: List<Village>,
    val staffAccounts: List<StaffAccount>,
    val schedules: List<MarriageSchedule>,
    val reminderSettings: ReminderSettings?
)

object KuaDataTransferManager {
    private const val BACKUP_FORMAT_VERSION = 2

    fun suggestedCsvFileName(config: MonthlyReportConfig): String {
        val period = MonthlyReportPdfGenerator.periodPrefix(config.year, config.month)
        val suffix = config.villageCode?.takeIf { it.isNotBlank() }
            ?.replace(Regex("[^a-zA-Z0-9_-]"), "")
            ?.let { "_$it" }
            .orEmpty()
        return "Rekap_Nikah_KUA_Donorojo_${period}${suffix}.csv"
    }

    fun suggestedBackupFileName(timestampMillis: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(timestampMillis))
        return "Backup_KUA_Donorojo_$stamp.json"
    }

    fun buildMonthlyCsvBytes(
        config: MonthlyReportConfig,
        allSchedules: List<MarriageSchedule>
    ): ByteArray {
        val filtered = MonthlyReportPdfGenerator.filterSchedules(allSchedules, config)
        val sb = StringBuilder()
        // UTF-8 BOM so Microsoft Excel on Windows/Android opens Indonesian characters cleanly
        sb.append('\uFEFF')
        sb.appendLine(
            listOf(
                "No",
                "No. Pendaftaran",
                "Tanggal Akad",
                "Jam (WIB)",
                "Calon Suami",
                "NIK Suami",
                "Calon Istri",
                "NIK Istri",
                "Desa",
                "Modin Desa",
                "Jenis Lokasi",
                "Alamat Akad",
                "Penghulu",
                "Status Wali",
                "Rincian Wali Nikah",
                "Alasan Wali Hakim",
                "Kelengkapan Berkas",
                "Daftar Berkas Lengkap",
                "Status Validasi",
                "Catatan Verifikasi",
                "Diverifikasi Oleh"
            ).joinToString(",") { escapeCsv(it) }
        )

        filtered.forEachIndexed { idx, s ->
            val checklist = s.resolvedChecklist()
            val row = listOf(
                (idx + 1).toString(),
                s.registrationNumber,
                s.weddingDate,
                s.weddingTime,
                s.groomName,
                excelSafeNumericText(s.groomNik),
                s.brideName,
                excelSafeNumericText(s.brideNik),
                s.villageName,
                s.modinName,
                s.resolvedLocationType().label,
                s.locationAddress,
                s.penghuluName,
                s.resolvedWaliStatus().label,
                s.waliSummaryText(),
                s.waliHakimReason,
                "${checklist.completedCount}/${checklist.totalCount} (${if (s.documentsComplete) "Lengkap" else "Belum"})",
                checklist.serialize(),
                s.resolvedStatus().label,
                s.verificationNote,
                s.verifiedByName
            )
            sb.appendLine(row.joinToString(",") { escapeCsv(it) })
        }
        return sb.toString().toByteArray(StandardCharsets.UTF_8)
    }

    fun buildBackupJsonBytes(
        villages: List<Village>,
        staffAccounts: List<StaffAccount>,
        schedules: List<MarriageSchedule>,
        settings: ReminderSettings
    ): ByteArray {
        val root = JSONObject().apply {
            put("formatVersion", BACKUP_FORMAT_VERSION)
            put("appName", "KUA Donorojo")
            put("exportedAtMillis", System.currentTimeMillis())

            put(
                "villages",
                JSONArray().apply {
                    villages.forEach { v ->
                        put(
                            JSONObject().apply {
                                put("code", v.code)
                                put("name", v.name)
                                put("modinName", v.modinName)
                                put("modinPhone", v.modinPhone)
                                put("updatedAt", v.updatedAt)
                            }
                        )
                    }
                }
            )

            put(
                "staffAccounts",
                JSONArray().apply {
                    staffAccounts.forEach { a ->
                        put(
                            JSONObject().apply {
                                put("id", a.id)
                                put("username", a.username)
                                put("passwordHash", a.passwordHash)
                                put("passwordSalt", a.passwordSalt)
                                put("role", a.role)
                                put("villageId", a.villageId)
                                put("villageName", a.villageName)
                                put("officerName", a.officerName)
                                put("whatsappNumber", a.whatsappNumber)
                                put("googleEmail", a.googleEmail)
                                put("active", a.active)
                                put("isWaliHakim", a.isWaliHakim)
                                put("createdAt", a.createdAt)
                                put("updatedAt", a.updatedAt)
                            }
                        )
                    }
                }
            )

            put(
                "schedules",
                JSONArray().apply {
                    schedules.forEach { s ->
                        put(
                            JSONObject().apply {
                                put("id", s.id)
                                put("registrationNumber", s.registrationNumber)
                                put("groomName", s.groomName)
                                put("groomNik", s.groomNik)
                                put("brideName", s.brideName)
                                put("brideNik", s.brideNik)
                                put("villageId", s.villageId)
                                put("villageName", s.villageName)
                                put("modinAccountId", s.modinAccountId)
                                put("modinName", s.modinName)
                                put("weddingDate", s.weddingDate)
                                put("weddingTime", s.weddingTime)
                                put("locationType", s.locationType)
                                put("locationAddress", s.locationAddress)
                                put("penghuluAccountId", s.penghuluAccountId)
                                put("penghuluName", s.penghuluName)
                                put("waliStatus", s.waliStatus)
                                put("waliRelationship", s.waliRelationship)
                                put("waliName", s.waliName)
                                put("waliBin", s.waliBin)
                                put("waliNik", s.waliNik)
                                put("waliAddress", s.waliAddress)
                                put("waliHakimAccountId", s.waliHakimAccountId)
                                put("waliHakimReason", s.waliHakimReason)
                                put("validationStatus", s.validationStatus)
                                put("documentsComplete", s.documentsComplete)
                                put("documentChecklistCodes", s.documentChecklistCodes)
                                put("verificationNote", s.verificationNote)
                                put("verifiedByName", s.verifiedByName)
                                put("createdAt", s.createdAt)
                                put("updatedAt", s.updatedAt)
                            }
                        )
                    }
                }
            )

            put(
                "reminderSettings",
                JSONObject().apply {
                    put("autoPhoneNotifEnabled", settings.autoPhoneNotifEnabled)
                    put("whatsappReminderEnabled", settings.whatsappReminderEnabled)
                    put("remindOnWeddingDay", settings.remindOnWeddingDay)
                    put("remindOneDayBefore", settings.remindOneDayBefore)
                    put("remindThreeDaysBefore", settings.remindThreeDaysBefore)
                    put("reminderTime", settings.reminderTime)
                    put("adminWhatsappNumber", settings.adminWhatsappNumber)
                    put("adminWhatsappApp", settings.adminWhatsappApp)
                    put("whatsappTemplate", settings.whatsappTemplate)
                    put("whatsappPenghuluTemplate", settings.whatsappPenghuluTemplate)
                    put("headOfKuaTitle", settings.headOfKuaTitle)
                    put("headOfKuaName", settings.headOfKuaName)
                    put("headOfKuaNip", settings.headOfKuaNip)
                    put("defaultWaliHakimAccountId", settings.defaultWaliHakimAccountId)
                    put("defaultWaliHakimName", settings.defaultWaliHakimName)
                    put("updatedByName", settings.updatedByName)
                    put("updatedAt", settings.updatedAt)
                }
            )
        }
        return root.toString(2).toByteArray(StandardCharsets.UTF_8)
    }

    fun parseBackupJson(bytes: ByteArray): Result<KuaBackupPayload> = runCatching {
        val text = String(bytes, StandardCharsets.UTF_8)
        val root = JSONObject(text)
        val exportedAt = root.optLong("exportedAtMillis", System.currentTimeMillis())
        val appTag = root.optString("appName", "KUA Donorojo")

        val villages = mutableListOf<Village>()
        root.optJSONArray("villages")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val code = o.optString("code").trim()
                val name = o.optString("name").trim()
                if (code.isNotBlank() && name.isNotBlank()) {
                    villages.add(
                        Village(
                            id = code,
                            code = code,
                            name = name,
                            modinName = o.optString("modinName"),
                            modinPhone = o.optString("modinPhone"),
                            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        }

        val staff = mutableListOf<StaffAccount>()
        root.optJSONArray("staffAccounts")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").trim()
                val username = o.optString("username").trim()
                val hash = o.optString("passwordHash")
                val salt = o.optString("passwordSalt")
                if (id.isNotBlank() && username.isNotBlank() && hash.length == 64 && salt.length >= 8) {
                    staff.add(
                        StaffAccount(
                            id = id,
                            username = username,
                            passwordHash = hash,
                            passwordSalt = salt,
                            role = o.optString("role", "MODIN"),
                            villageId = o.optString("villageId"),
                            villageName = o.optString("villageName"),
                            officerName = o.optString("officerName"),
                            whatsappNumber = o.optString("whatsappNumber"),
                            googleEmail = o.optString("googleEmail"),
                            active = o.optBoolean("active", true),
                            isWaliHakim = o.optBoolean("isWaliHakim", false),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        }

        val schedules = mutableListOf<MarriageSchedule>()
        root.optJSONArray("schedules")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").trim()
                val reg = o.optString("registrationNumber").trim()
                val groom = o.optString("groomName").trim()
                val bride = o.optString("brideName").trim()
                if (id.isNotBlank() && reg.isNotBlank() && groom.isNotBlank() && bride.isNotBlank()) {
                    schedules.add(
                        MarriageSchedule(
                            id = id,
                            registrationNumber = reg,
                            groomName = groom,
                            groomNik = o.optString("groomNik"),
                            brideName = bride,
                            brideNik = o.optString("brideNik"),
                            villageId = o.optString("villageId"),
                            villageName = o.optString("villageName"),
                            modinAccountId = o.optString("modinAccountId"),
                            modinName = o.optString("modinName"),
                            weddingDate = o.optString("weddingDate"),
                            weddingTime = o.optString("weddingTime"),
                            locationType = o.optString("locationType", "KUA"),
                            locationAddress = o.optString("locationAddress"),
                            penghuluAccountId = o.optString("penghuluAccountId"),
                            penghuluName = o.optString("penghuluName"),
                            waliStatus = o.optString("waliStatus", "WALI_NASAB"),
                            waliRelationship = o.optString("waliRelationship"),
                            waliName = o.optString("waliName"),
                            waliBin = o.optString("waliBin"),
                            waliNik = o.optString("waliNik"),
                            waliAddress = o.optString("waliAddress"),
                            waliHakimAccountId = o.optString("waliHakimAccountId"),
                            waliHakimReason = o.optString("waliHakimReason"),
                            validationStatus = o.optString("validationStatus", "MENUNGGU"),
                            documentsComplete = o.optBoolean("documentsComplete", false),
                            documentChecklistCodes = o.optString("documentChecklistCodes"),
                            verificationNote = o.optString("verificationNote"),
                            verifiedByName = o.optString("verifiedByName"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        }

        val settings = root.optJSONObject("reminderSettings")?.let { o ->
            ReminderSettings(
                id = ReminderSettings.GLOBAL_DOC_ID,
                autoPhoneNotifEnabled = o.optBoolean("autoPhoneNotifEnabled", true),
                whatsappReminderEnabled = o.optBoolean("whatsappReminderEnabled", true),
                remindOnWeddingDay = o.optBoolean("remindOnWeddingDay", true),
                remindOneDayBefore = o.optBoolean("remindOneDayBefore", true),
                remindThreeDaysBefore = o.optBoolean("remindThreeDaysBefore", false),
                reminderTime = o.optString("reminderTime", "07:00"),
                adminWhatsappNumber = o.optString("adminWhatsappNumber"),
                adminWhatsappApp = o.optString("adminWhatsappApp", "AUTO"),
                whatsappTemplate = o.optString("whatsappTemplate", ReminderSettings.DEFAULT_WA_TEMPLATE),
                whatsappPenghuluTemplate = o.optString(
                    "whatsappPenghuluTemplate",
                    ReminderSettings.DEFAULT_WA_PENGHULU_TEMPLATE
                ),
                headOfKuaTitle = o.optString("headOfKuaTitle", ReminderSettings.DEFAULT_HEAD_OF_KUA_TITLE),
                headOfKuaName = o.optString("headOfKuaName"),
                headOfKuaNip = o.optString("headOfKuaNip"),
                defaultWaliHakimAccountId = o.optString("defaultWaliHakimAccountId"),
                defaultWaliHakimName = o.optString("defaultWaliHakimName"),
                updatedByName = o.optString("updatedByName"),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
            )
        }

        if (villages.isEmpty() && staff.isEmpty() && schedules.isEmpty() && settings == null) {
            error("File backup tidak memuat data KUA Donorojo yang valid.")
        }

        KuaBackupPayload(
            exportedAtMillis = exportedAt,
            appVersionTag = appTag,
            villages = villages,
            staffAccounts = staff,
            schedules = schedules,
            reminderSettings = settings
        )
    }

    fun writeBytesToUri(context: Context, uri: Uri, bytes: ByteArray): Result<Unit> = runCatching {
        val stream = context.contentResolver.openOutputStream(uri)
            ?: error("Tidak dapat membuka file tujuan penyimpanan.")
        stream.use {
            it.write(bytes)
            it.flush()
        }
    }

    fun readBytesFromUri(context: Context, uri: Uri): Result<ByteArray> = runCatching {
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("Tidak dapat membaca file backup yang dipilih.")
        stream.use { it.readBytes() }
    }

    private fun excelSafeNumericText(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.length >= 12 && trimmed.all { it.isDigit() }) {
            "'$trimmed"
        } else {
            trimmed
        }
    }

    private fun escapeCsv(raw: String): String {
        val normalized = raw.replace("\r\n", "\n").replace("\r", "\n")
        val needsQuotes = normalized.contains(',') ||
            normalized.contains('"') ||
            normalized.contains('\n')
        return if (needsQuotes) {
            "\"${normalized.replace("\"", "\"\"")}\""
        } else {
            normalized
        }
    }
}
