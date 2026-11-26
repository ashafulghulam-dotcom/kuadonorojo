package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.LocationType
import com.example.data.MarriageSchedule
import com.example.data.ValidationStatus
import com.example.data.Village
import com.example.data.WaliStatus
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.EmeraldPrimaryContainer
import com.example.ui.theme.GoldSecondary
import com.example.ui.theme.GoldSecondaryContainer
import com.example.ui.theme.OutlineVariantSage
import com.example.ui.theme.StatusVerifiedFg
import com.example.ui.theme.TealTertiary
import com.example.ui.theme.TealTertiaryContainer
import com.example.util.MonthlyReportPdfGenerator
import java.util.Calendar
import java.util.Locale

@Composable
fun RecapAnalyticsSection(
    villages: List<Village>,
    schedules: List<MarriageSchedule>,
    modifier: Modifier = Modifier
) {
    val activeSchedules = remember(schedules) {
        schedules.filter { it.resolvedStatus() != ValidationStatus.DIBATALKAN }
    }
    val totalActive = activeSchedules.size
    val verifiedOrDone = remember(activeSchedules) {
        activeSchedules.count {
            it.resolvedStatus() == ValidationStatus.TERVERIFIKASI ||
                it.resolvedStatus() == ValidationStatus.SELESAI
        }
    }
    val completeDocsCount = remember(activeSchedules) {
        activeSchedules.count { it.documentsComplete || it.resolvedChecklist().isAllMandatoryComplete }
    }
    val kuaCount = remember(activeSchedules) {
        activeSchedules.count { it.resolvedLocationType() == LocationType.KUA }
    }
    val luarKuaCount = totalActive - kuaCount
    val waliHakimCount = remember(activeSchedules) {
        activeSchedules.count { it.resolvedWaliStatus() == WaliStatus.WALI_HAKIM }
    }
    val waliNasabCount = totalActive - waliHakimCount

    val villageBars = remember(villages, activeSchedules) {
        villages.map { v ->
            val count = activeSchedules.count { it.villageId == v.code }
            v.name.removePrefix("Desa ") to count
        }.sortedByDescending { it.second }
    }
    val maxVillageCount = remember(villageBars) {
        (villageBars.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    }

    val lastSixMonths = remember(activeSchedules) {
        val cal = Calendar.getInstance()
        val list = mutableListOf<Triple<String, String, Int>>()
        for (offset in 5 downTo 0) {
            val c = (cal.clone() as Calendar).apply { add(Calendar.MONTH, -offset) }
            val y = c.get(Calendar.YEAR)
            val m = c.get(Calendar.MONTH) + 1
            val prefix = String.format(Locale.US, "%04d-%02d", y, m)
            val shortMonth = MonthlyReportPdfGenerator.monthName(m).take(3)
            val count = activeSchedules.count { it.weddingDate.startsWith(prefix) }
            list.add(Triple(prefix, shortMonth, count))
        }
        list
    }
    val maxMonthlyCount = remember(lastSixMonths) {
        (lastSixMonths.maxOfOrNull { it.third } ?: 1).coerceAtLeast(1)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("recap_analytics_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // KPI Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnalyticsStatBox(
                label = "Total Aktif",
                value = totalActive.toString(),
                accent = EmeraldPrimary,
                bg = EmeraldPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            AnalyticsStatBox(
                label = "Sah / Selesai",
                value = verifiedOrDone.toString(),
                accent = StatusVerifiedFg,
                bg = Color(0xFFECFDF5),
                modifier = Modifier.weight(1f)
            )
            AnalyticsStatBox(
                label = "Berkas N1-N4",
                value = "$completeDocsCount/$totalActive",
                accent = TealTertiary,
                bg = TealTertiaryContainer,
                modifier = Modifier.weight(1f)
            )
        }

        // 6-Month Trend Column Chart
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            border = BorderStroke(1.dp, OutlineVariantSage),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TrendingUp,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Tren Akad Nikah 6 Bulan Terakhir",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(126.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    lastSixMonths.forEach { (_, monthLabel, count) ->
                        val ratio = (count.toFloat() / maxMonthlyCount.toFloat()).coerceIn(0f, 1f)
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (count > 0) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.68f)
                                    .height(82.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(if (count == 0) 0.08f else ratio.coerceAtLeast(0.14f))
                                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                        .background(
                                            if (count > 0) EmeraldPrimary else OutlineVariantSage
                                        )
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = monthLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 8-Village Horizontal Bar Chart
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            border = BorderStroke(1.dp, OutlineVariantSage),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BarChart,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Distribusi Jadwal per Desa (8 Desa)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                villageBars.forEach { (villageName, count) ->
                    val fraction = (count.toFloat() / maxVillageCount.toFloat()).coerceIn(0f, 1f)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = villageName,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$count akad",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (count > 0) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(OutlineVariantSage.copy(alpha = 0.7f))
                        ) {
                            if (count > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction.coerceAtLeast(0.06f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(EmeraldPrimary)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Location & Wali Breakdown Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BreakdownCard(
                icon = Icons.Filled.LocationCity,
                title = "Lokasi Akad",
                firstLabel = "Balai KUA",
                firstValue = kuaCount,
                secondLabel = "Luar KUA",
                secondValue = luarKuaCount,
                accent = TealTertiary,
                modifier = Modifier.weight(1f)
            )
            BreakdownCard(
                icon = Icons.Filled.Gavel,
                title = "Status Wali",
                firstLabel = "Wali Nasab",
                firstValue = waliNasabCount,
                secondLabel = "Wali Hakim",
                secondValue = waliHakimCount,
                accent = GoldSecondary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AnalyticsStatBox(
    label: String,
    value: String,
    accent: Color,
    bg: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accent.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BreakdownCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    firstLabel: String,
    firstValue: Int,
    secondLabel: String,
    secondValue: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val total = (firstValue + secondValue).coerceAtLeast(1)
    val firstPct = ((firstValue * 100) / total)
    val secondPct = if (firstValue + secondValue == 0) 0 else (100 - firstPct)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, OutlineVariantSage),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(firstLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$firstValue ($firstPct%)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(secondLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$secondValue ($secondPct%)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = accent)
            }
        }
    }
}
