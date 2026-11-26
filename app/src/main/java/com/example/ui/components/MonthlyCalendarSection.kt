package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.MarriageSchedule
import com.example.data.ValidationStatus
import com.example.data.formatIndonesianDate
import com.example.data.todayIsoDate
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.EmeraldPrimaryContainer
import com.example.ui.theme.GoldSecondary
import com.example.ui.theme.OutlineVariantSage
import com.example.util.MonthlyReportPdfGenerator
import java.util.Calendar
import java.util.Locale

@Composable
fun MonthlyCalendarSection(
    schedules: List<MarriageSchedule>,
    selectedIsoDate: String?,
    onSelectDate: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val todayIso = remember { todayIsoDate() }
    val initialCal = remember { Calendar.getInstance() }
    var displayedYear by remember { mutableIntStateOf(initialCal.get(Calendar.YEAR)) }
    var displayedMonth by remember { mutableIntStateOf(initialCal.get(Calendar.MONTH) + 1) } // 1..12

    val monthPrefix = remember(displayedYear, displayedMonth) {
        String.format(Locale.US, "%04d-%02d", displayedYear, displayedMonth)
    }

    val countsByDate = remember(schedules, monthPrefix) {
        schedules
            .filter { it.weddingDate.startsWith(monthPrefix) && it.resolvedStatus() != ValidationStatus.DIBATALKAN }
            .groupingBy { it.weddingDate }
            .eachCount()
    }

    val monthTotalCount = remember(countsByDate) { countsByDate.values.sum() }

    val calendarCells = remember(displayedYear, displayedMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, displayedYear)
            set(Calendar.MONTH, displayedMonth - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        // Shift Sunday=1..Saturday=7 -> Monday=0..Sunday=6
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val leadingBlanks = (firstDayOfWeek + 5) % 7
        val list = mutableListOf<Int?>()
        repeat(leadingBlanks) { list.add(null) }
        for (day in 1..daysInMonth) list.add(day)
        while (list.size % 7 != 0) list.add(null)
        list
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, OutlineVariantSage),
        modifier = modifier
            .fillMaxWidth()
            .testTag("monthly_calendar_section")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        if (displayedMonth == 1) {
                            displayedMonth = 12
                            displayedYear -= 1
                        } else {
                            displayedMonth -= 1
                        }
                    },
                    modifier = Modifier.testTag("calendar_prev_month_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.cd_calendar_prev_month),
                        tint = EmeraldPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${MonthlyReportPdfGenerator.monthName(displayedMonth)} $displayedYear",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$monthTotalCount jadwal akad aktif bulan ini",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        if (displayedMonth == 12) {
                            displayedMonth = 1
                            displayedYear += 1
                        } else {
                            displayedMonth += 1
                        }
                    },
                    modifier = Modifier.testTag("calendar_next_month_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.cd_calendar_next_month),
                        tint = EmeraldPrimary
                    )
                }
            }

            // Day of week headers
            val dayHeaders = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
            Row(modifier = Modifier.fillMaxWidth()) {
                dayHeaders.forEachIndexed { idx, label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (idx == 6) Color(0xFFBE123C) else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Calendar weeks
            calendarCells.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    week.forEach { day ->
                        if (day == null) {
                            Spacer(modifier = Modifier.weight(1f).height(46.dp))
                        } else {
                            val isoDate = String.format(Locale.US, "%04d-%02d-%02d", displayedYear, displayedMonth, day)
                            val count = countsByDate[isoDate] ?: 0
                            val isSelected = selectedIsoDate == isoDate
                            val isToday = isoDate == todayIso

                            val bgColor = when {
                                isSelected -> EmeraldPrimary
                                count > 0 -> EmeraldPrimaryContainer
                                isToday -> Color(0xFFFDF6E7)
                                else -> Color.Transparent
                            }
                            val textColor = when {
                                isSelected -> Color.White
                                count > 0 -> EmeraldPrimary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            val borderColor = when {
                                isSelected -> EmeraldPrimary
                                isToday -> GoldSecondary
                                count > 0 -> EmeraldPrimary.copy(alpha = 0.28f)
                                else -> Color.Transparent
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = bgColor,
                                border = if (borderColor != Color.Transparent) BorderStroke(1.dp, borderColor) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onSelectDate(if (isSelected) null else isoDate)
                                    }
                                    .testTag("calendar_day_$isoDate")
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = day.toString(),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (isSelected || isToday || count > 0) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = textColor
                                    )
                                    if (count > 0) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color.White else EmeraldPrimary)
                                                .padding(horizontal = 5.dp, vertical = 1.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = count.toString(),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (isSelected) EmeraldPrimary else Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Selected date footer chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (selectedIsoDate != null) {
                            "Filter: ${formatIndonesianDate(selectedIsoDate)}"
                        } else {
                            "Ketuk tanggal untuk melihat jadwal hari tersebut"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (selectedIsoDate != null) {
                    Text(
                        text = "Tampilkan Semua",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = EmeraldPrimary,
                        modifier = Modifier
                            .clickable { onSelectDate(null) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("calendar_clear_date_filter")
                    )
                }
            }
        }
    }
}
