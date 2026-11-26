package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.LocationType
import com.example.data.MarriageSchedule
import com.example.data.StaffAccount
import com.example.data.ValidationStatus
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.EmeraldPrimaryContainer
import com.example.ui.theme.GoldSecondary
import com.example.ui.theme.GoldSecondaryContainer
import com.example.ui.theme.OutlineVariantSage
import com.example.ui.theme.SurfaceVariantSage
import com.example.ui.theme.TealTertiary
import com.example.ui.theme.TealTertiaryContainer
import java.util.Calendar
import java.util.Locale

private val MONTH_SHORT_LABELS = listOf(
    "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
    "Jul", "Agt", "Sep", "Okt", "Nov", "Des"
)

private val MONTH_FULL_LABELS = listOf(
    "Januari", "Februari", "Maret", "April", "Mei", "Juni",
    "Juli", "Agustus", "September", "Oktober", "November", "Desember"
)

private const val ALL_PENGHULU_FILTER_ID = "__ALL_PENGHULU__"

private data class PenghuluChartOption(
    val id: String,
    val name: String,
    val isAllOption: Boolean = false
)

private data class MonthlyLocationBucket(
    val monthIndex: Int, // 0..11
    val kuaCount: Int,
    val luarKuaCount: Int
) {
    val total: Int get() = kuaCount + luarKuaCount
}

private data class PenghuluMonthlySummaryRow(
    val option: PenghuluChartOption,
    val kuaCount: Int,
    val luarKuaCount: Int
) {
    val total: Int get() = kuaCount + luarKuaCount
}

private fun parseIsoYearMonth(isoDate: String): Pair<Int, Int>? {
    val parts = isoDate.trim().split("-")
    if (parts.size < 2) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    return year to (month - 1)
}

private fun scheduleMatchesPenghulu(
    schedule: MarriageSchedule,
    penghuluId: String,
    penghuluName: String
): Boolean {
    if (penghuluId == ALL_PENGHULU_FILTER_ID) return true
    if (schedule.penghuluAccountId == penghuluId || schedule.waliHakimAccountId == penghuluId) {
        return true
    }
    return schedule.penghuluAccountId.isBlank() &&
        schedule.penghuluName.isNotBlank() &&
        schedule.penghuluName.trim().equals(penghuluName.trim(), ignoreCase = true)
}

@Composable
fun PenghuluMonthlyRecapChartCard(
    allSchedules: List<MarriageSchedule>,
    penghuluAccounts: List<StaffAccount>,
    defaultPenghuluId: String?,
    defaultToAllPenghulu: Boolean = false,
    modifier: Modifier = Modifier
) {
    val calendarNow = remember { Calendar.getInstance() }
    val currentYear = remember { calendarNow.get(Calendar.YEAR) }
    val currentMonthIndex = remember { calendarNow.get(Calendar.MONTH) }

    var selectedYear by remember { mutableIntStateOf(currentYear) }
    var selectedMonthIndex by remember { mutableIntStateOf(currentMonthIndex) }

    val chartOptions = remember(penghuluAccounts, allSchedules) {
        val list = mutableListOf<PenghuluChartOption>()
        val knownNames = mutableSetOf<String>()

        penghuluAccounts.forEach { acc ->
            list.add(PenghuluChartOption(id = acc.id, name = acc.officerName))
            knownNames.add(acc.officerName.trim().lowercase(Locale.ROOT))
        }

        // Also include any historical penghulu names from schedules not linked to a current account
        allSchedules
            .asSequence()
            .filter { it.penghuluAccountId.isBlank() && it.penghuluName.isNotBlank() }
            .map { it.penghuluName.trim() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .forEach { rawName ->
                if (knownNames.add(rawName.lowercase(Locale.ROOT))) {
                    list.add(PenghuluChartOption(id = "name_$rawName", name = rawName))
                }
            }

        list.add(
            PenghuluChartOption(
                id = ALL_PENGHULU_FILTER_ID,
                name = "Semua Penghulu",
                isAllOption = true
            )
        )
        list
    }

    var selectedPenghuluId by remember(defaultPenghuluId, defaultToAllPenghulu) {
        mutableStateOf(
            if (defaultToAllPenghulu) {
                ALL_PENGHULU_FILTER_ID
            } else {
                defaultPenghuluId?.takeIf { id -> chartOptions.any { it.id == id } }
                    ?: chartOptions.firstOrNull()?.id
                    ?: ALL_PENGHULU_FILTER_ID
            }
        )
    }

    LaunchedEffect(defaultPenghuluId, defaultToAllPenghulu, chartOptions) {
        if (defaultToAllPenghulu) {
            if (chartOptions.none { it.id == selectedPenghuluId }) {
                selectedPenghuluId = ALL_PENGHULU_FILTER_ID
            }
        } else if (!defaultPenghuluId.isNullOrBlank() && chartOptions.any { it.id == defaultPenghuluId }) {
            selectedPenghuluId = defaultPenghuluId
        } else if (chartOptions.none { it.id == selectedPenghuluId }) {
            selectedPenghuluId = chartOptions.firstOrNull()?.id ?: ALL_PENGHULU_FILTER_ID
        }
    }

    val selectedOption = remember(chartOptions, selectedPenghuluId) {
        chartOptions.firstOrNull { it.id == selectedPenghuluId }
            ?: chartOptions.last()
    }

    // Active (non-cancelled) schedules for the chosen year
    val yearSchedules = remember(allSchedules, selectedYear) {
        allSchedules.filter { item ->
            item.resolvedStatus() != ValidationStatus.DIBATALKAN &&
                parseIsoYearMonth(item.weddingDate)?.first == selectedYear
        }
    }

    // Schedules for selected Penghulu (or all) in the chosen year
    val scopedYearSchedules = remember(yearSchedules, selectedOption) {
        yearSchedules.filter { item ->
            scheduleMatchesPenghulu(item, selectedOption.id, selectedOption.name)
        }
    }

    val monthlyBuckets = remember(scopedYearSchedules) {
        val kuaCounts = IntArray(12)
        val luarCounts = IntArray(12)
        scopedYearSchedules.forEach { item ->
            val ym = parseIsoYearMonth(item.weddingDate) ?: return@forEach
            val m = ym.second
            when (item.resolvedLocationType()) {
                LocationType.KUA -> kuaCounts[m]++
                LocationType.LUAR_KUA -> luarCounts[m]++
            }
        }
        (0..11).map { m ->
            MonthlyLocationBucket(
                monthIndex = m,
                kuaCount = kuaCounts[m],
                luarKuaCount = luarCounts[m]
            )
        }
    }

    val yearTotalKua = remember(monthlyBuckets) { monthlyBuckets.sumOf { it.kuaCount } }
    val yearTotalLuar = remember(monthlyBuckets) { monthlyBuckets.sumOf { it.luarKuaCount } }
    val yearGrandTotal = yearTotalKua + yearTotalLuar
    val maxSingleBarValue = remember(monthlyBuckets) {
        maxOf(1, monthlyBuckets.maxOf { maxOf(it.kuaCount, it.luarKuaCount) })
    }

    val selectedMonthBucket = monthlyBuckets.getOrElse(selectedMonthIndex) {
        MonthlyLocationBucket(selectedMonthIndex, 0, 0)
    }

    // Breakdown of EACH individual Penghulu for the selected month & year
    val perPenghuluMonthRows = remember(yearSchedules, chartOptions, selectedMonthIndex) {
        val monthSchedules = yearSchedules.filter {
            parseIsoYearMonth(it.weddingDate)?.second == selectedMonthIndex
        }
        chartOptions
            .filter { !it.isAllOption }
            .map { option ->
                val matched = monthSchedules.filter {
                    scheduleMatchesPenghulu(it, option.id, option.name)
                }
                val kua = matched.count { it.resolvedLocationType() == LocationType.KUA }
                val luar = matched.count { it.resolvedLocationType() == LocationType.LUAR_KUA }
                PenghuluMonthlySummaryRow(
                    option = option,
                    kuaCount = kua,
                    luarKuaCount = luar
                )
            }
            .sortedByDescending { it.total }
    }

    val barScrollState = rememberScrollState()

    // Auto-scroll bar chart toward current month on initial composition
    LaunchedEffect(Unit) {
        if (currentMonthIndex >= 5) {
            barScrollState.scrollTo(((currentMonthIndex - 3).coerceAtLeast(0)) * 96)
        }
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, OutlineVariantSage),
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("penghulu_monthly_recap_chart_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White,
                            SurfaceVariantSage.copy(alpha = 0.35f),
                            Color.White
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row + Year switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = EmeraldPrimaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.BarChart,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.penghulu_chart_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.penghulu_chart_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceVariantSage,
                    border = BorderStroke(1.dp, OutlineVariantSage)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { selectedYear -= 1 },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("penghulu_chart_prev_year")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = stringResource(R.string.cd_chart_prev_year),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = selectedYear.toString(),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { selectedYear += 1 },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("penghulu_chart_next_year")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.cd_chart_next_year),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Per-Penghulu filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chartOptions.forEach { option ->
                    val isSelected = option.id == selectedOption.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedPenghuluId = option.id },
                        leadingIcon = {
                            Icon(
                                imageVector = if (option.isAllOption) Icons.Filled.Groups else Icons.Filled.Person,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = option.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldPrimaryContainer,
                            selectedLabelColor = EmeraldPrimary,
                            selectedLeadingIconColor = EmeraldPrimary
                        ),
                        modifier = Modifier.testTag("penghulu_chart_chip_${option.id}")
                    )
                }
            }

            // Yearly KPI Summary Strip for selected Penghulu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChartStatMiniCard(
                    label = "Total $selectedYear",
                    value = "$yearGrandTotal",
                    subLabel = "Akad aktif",
                    containerColor = TealTertiaryContainer,
                    accentColor = TealTertiary,
                    modifier = Modifier.weight(1f)
                )
                ChartStatMiniCard(
                    label = stringResource(R.string.penghulu_chart_balai_kua),
                    value = "$yearTotalKua",
                    subLabel = if (yearGrandTotal > 0) "${(yearTotalKua * 100) / yearGrandTotal}%" else "0%",
                    containerColor = EmeraldPrimaryContainer,
                    accentColor = EmeraldPrimary,
                    modifier = Modifier.weight(1f)
                )
                ChartStatMiniCard(
                    label = stringResource(R.string.penghulu_chart_luar_kua),
                    value = "$yearTotalLuar",
                    subLabel = if (yearGrandTotal > 0) "${(yearTotalLuar * 100) / yearGrandTotal}%" else "0%",
                    containerColor = GoldSecondaryContainer,
                    accentColor = GoldSecondary,
                    modifier = Modifier.weight(1f)
                )
            }

            // Legend row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChartLegendDot(
                        color = EmeraldPrimary,
                        label = stringResource(R.string.penghulu_chart_balai_kua)
                    )
                    ChartLegendDot(
                        color = GoldSecondary,
                        label = stringResource(R.string.penghulu_chart_luar_kua)
                    )
                }
                Text(
                    text = "Ketuk bulan untuk detail",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 12-Month Grouped Bar Chart (Scrollable with crisp Canvas gridlines & animated bars)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, OutlineVariantSage),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(196.dp)
                        .padding(top = 12.dp, bottom = 10.dp, start = 8.dp, end = 8.dp)
                ) {
                    val gridColor = OutlineVariantSage
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 18.dp, bottom = 28.dp)
                    ) {
                        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        val levels = listOf(0f, 0.33f, 0.66f, 1f)
                        levels.forEachIndexed { idx, frac ->
                            val y = size.height * frac
                            drawLine(
                                color = if (idx == levels.lastIndex) gridColor else gridColor.copy(alpha = 0.7f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = if (idx == levels.lastIndex) 2f else 1.2f,
                                pathEffect = if (idx == levels.lastIndex) null else dash
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(barScrollState),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        monthlyBuckets.forEach { bucket ->
                            val isMonthSelected = bucket.monthIndex == selectedMonthIndex
                            val kuaFraction by animateFloatAsState(
                                targetValue = bucket.kuaCount.toFloat() / maxSingleBarValue.toFloat(),
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                ),
                                label = "kua_bar_${bucket.monthIndex}"
                            )
                            val luarFraction by animateFloatAsState(
                                targetValue = bucket.luarKuaCount.toFloat() / maxSingleBarValue.toFloat(),
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                ),
                                label = "luar_bar_${bucket.monthIndex}"
                            )

                            Column(
                                modifier = Modifier
                                    .width(50.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isMonthSelected) {
                                            EmeraldPrimaryContainer.copy(alpha = 0.55f)
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .clickable { selectedMonthIndex = bucket.monthIndex }
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                                    .testTag("penghulu_chart_month_${bucket.monthIndex + 1}"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                // Value count label row above bars
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (bucket.kuaCount > 0) "${bucket.kuaCount}" else "",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = EmeraldPrimary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = if (bucket.luarKuaCount > 0) "${bucket.luarKuaCount}" else "",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = GoldSecondary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Grouped 2-bar Canvas (Balai KUA vs Luar KUA)
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    val barGap = 4.dp.toPx()
                                    val barWidth = ((size.width - barGap) / 2f).coerceAtLeast(4f)
                                    val corner = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                                    val minStubHeight = 3.dp.toPx()

                                    // Left bar: Balai KUA
                                    val kuaHeight = if (bucket.kuaCount > 0) {
                                        (size.height * kuaFraction).coerceAtLeast(8.dp.toPx())
                                    } else {
                                        minStubHeight
                                    }
                                    drawRoundRect(
                                        color = if (bucket.kuaCount > 0) {
                                            EmeraldPrimary
                                        } else {
                                            EmeraldPrimary.copy(alpha = 0.14f)
                                        },
                                        topLeft = Offset(0f, size.height - kuaHeight),
                                        size = Size(barWidth, kuaHeight),
                                        cornerRadius = corner
                                    )

                                    // Right bar: Luar KUA
                                    val luarHeight = if (bucket.luarKuaCount > 0) {
                                        (size.height * luarFraction).coerceAtLeast(8.dp.toPx())
                                    } else {
                                        minStubHeight
                                    }
                                    drawRoundRect(
                                        color = if (bucket.luarKuaCount > 0) {
                                            GoldSecondary
                                        } else {
                                            GoldSecondary.copy(alpha = 0.16f)
                                        },
                                        topLeft = Offset(barWidth + barGap, size.height - luarHeight),
                                        size = Size(barWidth, luarHeight),
                                        cornerRadius = corner
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = MONTH_SHORT_LABELS[bucket.monthIndex],
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isMonthSelected) FontWeight.ExtraBold else FontWeight.Medium
                                    ),
                                    color = if (isMonthSelected) {
                                        EmeraldPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Selected Month Detail Pill
            val monthFullName = MONTH_FULL_LABELS.getOrElse(selectedMonthIndex) { "" }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = EmeraldPrimaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.22f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Detail $monthFullName $selectedYear",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = selectedOption.name,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = EmeraldPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.HomeWork,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Balai KUA: ${selectedMonthBucket.kuaCount}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = GoldSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Luar KUA: ${selectedMonthBucket.luarKuaCount}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, OutlineVariantSage)
                        ) {
                            Text(
                                text = "Total ${selectedMonthBucket.total}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Per-Penghulu breakdown for the selected month (so every penghulu's Balai KUA vs Luar KUA is comparable at a glance)
            if (perPenghuluMonthRows.isNotEmpty()) {
                HorizontalDivider(color = OutlineVariantSage)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Rekap Masing-Masing Penghulu — $monthFullName $selectedYear",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val maxRowTotal = maxOf(1, perPenghuluMonthRows.maxOf { it.total })
                    perPenghuluMonthRows.forEach { row ->
                        val isRowSelected = row.option.id == selectedOption.id
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isRowSelected) {
                                EmeraldPrimaryContainer.copy(alpha = 0.45f)
                            } else {
                                SurfaceVariantSage.copy(alpha = 0.55f)
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isRowSelected) EmeraldPrimary.copy(alpha = 0.35f) else OutlineVariantSage
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedPenghuluId = row.option.id }
                                .testTag("penghulu_monthly_row_${row.option.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = row.option.name,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "KUA ${row.kuaCount}  •  Luar ${row.luarKuaCount}  (${row.total})",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Proportional stacked horizontal bar (Balai KUA + Luar KUA)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White)
                                ) {
                                    if (row.total > 0) {
                                        val totalShare = row.total.toFloat() / maxRowTotal.toFloat()
                                        val kuaPart = row.kuaCount.toFloat() / row.total.toFloat()
                                        val luarPart = row.luarKuaCount.toFloat() / row.total.toFloat()
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth(totalShare.coerceIn(0.06f, 1f))
                                                .fillMaxHeight()
                                        ) {
                                            if (row.kuaCount > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(kuaPart.coerceAtLeast(0.01f))
                                                        .fillMaxHeight()
                                                        .background(EmeraldPrimary)
                                                )
                                            }
                                            if (row.luarKuaCount > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(luarPart.coerceAtLeast(0.01f))
                                                        .fillMaxHeight()
                                                        .background(GoldSecondary)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartStatMiniCard(
    label: String,
    value: String,
    subLabel: String,
    containerColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.22f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ChartLegendDot(
    color: Color,
    label: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
