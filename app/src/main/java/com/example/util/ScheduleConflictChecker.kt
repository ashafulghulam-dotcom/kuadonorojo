package com.example.util

import com.example.data.LocationType
import com.example.data.MarriageSchedule
import com.example.data.ValidationStatus
import com.example.data.WaliStatus
import kotlin.math.abs

enum class ConflictSeverity {
    CRITICAL,
    WARNING
}

data class ScheduleConflict(
    val severity: ConflictSeverity,
    val title: String,
    val detail: String,
    val conflictingSchedule: MarriageSchedule
)

object ScheduleConflictChecker {
    private const val PENGHULU_BUFFER_MINUTES = 90
    private const val BALAI_KUA_BUFFER_MINUTES = 45

    fun parseMinutesOfDay(hhMm: String): Int? {
        val parts = hhMm.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    fun detectConflicts(
        editingScheduleId: String?,
        weddingDate: String,
        weddingTime: String,
        locationType: LocationType,
        penghuluAccountId: String,
        penghuluName: String,
        waliStatus: WaliStatus,
        waliHakimAccountId: String,
        waliHakimName: String,
        existingSchedules: List<MarriageSchedule>
    ): List<ScheduleConflict> {
        val cleanDate = weddingDate.trim()
        val targetMin = parseMinutesOfDay(weddingTime) ?: return emptyList()
        if (cleanDate.length != 10) return emptyList()

        val cleanPenghuluId = penghuluAccountId.trim()
        val cleanPenghuluName = penghuluName.trim()
        val cleanWaliHakimId = if (waliStatus == WaliStatus.WALI_HAKIM) waliHakimAccountId.trim() else ""
        val cleanWaliHakimName = if (waliStatus == WaliStatus.WALI_HAKIM) waliHakimName.trim() else ""

        val conflicts = mutableListOf<ScheduleConflict>()

        existingSchedules.forEach { other ->
            if (!editingScheduleId.isNullOrBlank() && other.id == editingScheduleId) return@forEach
            if (other.resolvedStatus() == ValidationStatus.DIBATALKAN) return@forEach
            if (other.weddingDate != cleanDate) return@forEach

            val otherMin = parseMinutesOfDay(other.weddingTime) ?: return@forEach
            val diffMin = abs(targetMin - otherMin)

            // 1. Penghulu duty overlap check (±90 min)
            val samePenghulu = (cleanPenghuluId.isNotBlank() && other.penghuluAccountId == cleanPenghuluId) ||
                (cleanPenghuluId.isBlank() && cleanPenghuluName.isNotBlank() &&
                    other.penghuluName.equals(cleanPenghuluName, ignoreCase = true)) ||
                (cleanPenghuluId.isNotBlank() && other.resolvedWaliStatus() == WaliStatus.WALI_HAKIM &&
                    other.waliHakimAccountId == cleanPenghuluId)

            if (samePenghulu && diffMin < PENGHULU_BUFFER_MINUTES) {
                val sev = if (diffMin <= 30) ConflictSeverity.CRITICAL else ConflictSeverity.WARNING
                val label = cleanPenghuluName.ifBlank { other.penghuluName.ifBlank { "Penghulu" } }
                conflicts.add(
                    ScheduleConflict(
                        severity = sev,
                        title = "Bentrok Jadwal Penghulu ($label)",
                        detail = "Sudah terjadwal pukul ${other.weddingTime} WIB (${other.groomName} & ${other.brideName}, ${other.villageName}) — selisih $diffMin menit.",
                        conflictingSchedule = other
                    )
                )
            }

            // 2. Wali Hakim overlap check (±90 min) when distinct from Penghulu
            if (waliStatus == WaliStatus.WALI_HAKIM && (cleanWaliHakimId.isNotBlank() || cleanWaliHakimName.isNotBlank())) {
                val sameAsPenghulu = cleanWaliHakimId.isNotBlank() && cleanWaliHakimId == cleanPenghuluId
                if (!sameAsPenghulu) {
                    val waliOverlap = (cleanWaliHakimId.isNotBlank() &&
                        (other.waliHakimAccountId == cleanWaliHakimId || other.penghuluAccountId == cleanWaliHakimId)) ||
                        (cleanWaliHakimId.isBlank() && cleanWaliHakimName.isNotBlank() &&
                            (other.penghuluName.equals(cleanWaliHakimName, ignoreCase = true) ||
                                (other.resolvedWaliStatus() == WaliStatus.WALI_HAKIM &&
                                    other.waliName.equals(cleanWaliHakimName, ignoreCase = true))))
                    if (waliOverlap && diffMin < PENGHULU_BUFFER_MINUTES) {
                        val label = cleanWaliHakimName.ifBlank { "Penghulu Wali Hakim" }
                        conflicts.add(
                            ScheduleConflict(
                                severity = if (diffMin <= 30) ConflictSeverity.CRITICAL else ConflictSeverity.WARNING,
                                title = "Bentrok Tugas Wali Hakim ($label)",
                                detail = "Sudah bertugas pukul ${other.weddingTime} WIB (${other.groomName} & ${other.brideName}) — selisih $diffMin menit.",
                                conflictingSchedule = other
                            )
                        )
                    }
                }
            }

            // 3. Balai Nikah KUA room overlap check (±45 min)
            if (locationType == LocationType.KUA &&
                other.resolvedLocationType() == LocationType.KUA &&
                diffMin < BALAI_KUA_BUFFER_MINUTES
            ) {
                conflicts.add(
                    ScheduleConflict(
                        severity = if (diffMin <= 15) ConflictSeverity.CRITICAL else ConflictSeverity.WARNING,
                        title = "Antrean Balai Nikah KUA Padat",
                        detail = "Ada akad di Balai Nikah KUA pukul ${other.weddingTime} WIB (${other.groomName} & ${other.brideName}) — selisih $diffMin menit.",
                        conflictingSchedule = other
                    )
                )
            }
        }

        return conflicts.sortedBy { if (it.severity == ConflictSeverity.CRITICAL) 0 else 1 }
    }
}
