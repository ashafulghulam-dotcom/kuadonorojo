package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.MarriageSchedule
import com.example.data.ReminderSettings
import com.example.data.Village
import com.example.util.MonthlyReportConfig
import com.example.util.MonthlyReportPdfGenerator
import com.example.util.PdfSaveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyReportSheet(
    reminderSettings: ReminderSettings,
    villages: List<Village>,
    schedules: List<MarriageSchedule>,
    onOpenHeadOfKuaSettings: () -> Unit,
    onDismiss: () -> Unit,
    onPostMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val initialCalendar = remember { Calendar.getInstance() }
    var selectedYear by remember { mutableIntStateOf(initialCalendar.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(initialCalendar.get(Calendar.MONTH) + 1) }
    var selectedVillageCode by remember { mutableStateOf<String?>(null) }
    var onlyVerifiedOrDone by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }

    val liveDownloadDateText = remember {
        MonthlyReportPdfGenerator.formatDownloadDateFull(System.currentTimeMillis())
    }

    val selectedVillageLabel = remember(selectedVillageCode, villages) {
        if (selectedVillageCode == null) {
            "Semua 8 Desa"
        } else {
            villages.firstOrNull { it.code == selectedVillageCode }?.name ?: "Desa Terpilih"
        }
    }

    val reportConfig = remember(
        selectedYear,
        selectedMonth,
        selectedVillageCode,
        selectedVillageLabel,
        onlyVerifiedOrDone,
        reminderSettings.headOfKuaTitle,
        reminderSettings.headOfKuaName,
        reminderSettings.headOfKuaNip
    ) {
        MonthlyReportConfig(
            year = selectedYear,
            month = selectedMonth,
            villageCode = selectedVillageCode,
            villageLabel = selectedVillageLabel,
            onlyVerifiedOrDone = onlyVerifiedOrDone,
            signerTitle = reminderSettings.headOfKuaTitle.ifBlank { ReminderSettings.DEFAULT_HEAD_OF_KUA_TITLE },
            signerName = reminderSettings.headOfKuaName,
            signerNip = reminderSettings.headOfKuaNip
        )
    }

    val filteredSchedules = remember(schedules, reportConfig) {
        MonthlyReportPdfGenerator.filterSchedules(schedules, reportConfig)
    }
    val stats = remember(filteredSchedules) {
        MonthlyReportPdfGenerator.computeStats(filteredSchedules)
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isGenerating = true
        scope.launch {
            val downloadNow = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = MonthlyReportPdfGenerator.buildPdfBytes(
                        context = context,
                        config = reportConfig,
                        villages = villages,
                        allSchedules = schedules,
                        downloadTimestampMillis = downloadNow
                    )
                    MonthlyReportPdfGenerator.writeToUri(context, uri, bytes).getOrThrow()
                }
            }
            isGenerating = false
            result.onSuccess {
                onPostMessage(
                    "Laporan A4 ${MonthlyReportPdfGenerator.monthName(selectedMonth)} $selectedYear berhasil disimpan."
                )
                onDismiss()
            }.onFailure { err ->
                onPostMessage(err.localizedMessage ?: "Gagal menyimpan file PDF.")
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PictureAsPdf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.report_monthly_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.report_monthly_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("close_monthly_report_sheet")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_cancel)
                    )
                }
            }

            // Month & Year Picker
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Periode Laporan",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { selectedYear = (selectedYear - 1).coerceAtLeast(2020) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("report_prev_year")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ChevronLeft,
                                    contentDescription = stringResource(R.string.cd_report_prev_year)
                                )
                            }
                            Text(
                                text = selectedYear.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = { selectedYear = (selectedYear + 1).coerceAtMost(2035) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("report_next_year")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = stringResource(R.string.cd_report_next_year)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MonthlyReportPdfGenerator.INDONESIAN_MONTHS.forEachIndexed { idx, monthLabel ->
                            val monthNum = idx + 1
                            FilterChip(
                                selected = selectedMonth == monthNum,
                                onClick = { selectedMonth = monthNum },
                                label = { Text(monthLabel) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("report_month_$monthNum")
                            )
                        }
                    }
                }
            }

            // Village Filter
            if (villages.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Cakupan Wilayah Desa",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedVillageCode == null,
                            onClick = { selectedVillageCode = null },
                            label = { Text("Semua 8 Desa") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                        villages.forEach { village ->
                            FilterChip(
                                selected = selectedVillageCode == village.code,
                                onClick = {
                                    selectedVillageCode =
                                        if (selectedVillageCode == village.code) null else village.code
                                },
                                label = { Text(village.name.removePrefix("Desa ")) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                        }
                    }
                }
            }

            // Status filter toggle
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hanya Terverifikasi & Selesai",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Sembunyikan jadwal menunggu, revisi, atau batal dari tabel laporan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = onlyVerifiedOrDone,
                        onCheckedChange = { onlyVerifiedOrDone = it },
                        modifier = Modifier.testTag("report_only_verified_switch")
                    )
                }
            }

            // Live Period Preview Summary + Auto Download Date Indicator
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Pratinjau Data • ${MonthlyReportPdfGenerator.monthName(selectedMonth)} $selectedYear",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ReportMiniStat(label = "Total", value = stats.total.toString(), modifier = Modifier.weight(1f))
                        ReportMiniStat(label = "Balai KUA", value = stats.atKua.toString(), modifier = Modifier.weight(1f))
                        ReportMiniStat(label = "Luar KUA", value = stats.outsideKua.toString(), modifier = Modifier.weight(1f))
                        ReportMiniStat(label = "Sah/Selesai", value = stats.verifiedOrDone.toString(), modifier = Modifier.weight(1f))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Tanggal laporan otomatis: $liveDownloadDateText",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = if (stats.total == 0) {
                            "Tidak ada jadwal pada bulan ini (PDF tetap dapat dicetak sebagai Laporan Nihil resmi A4)."
                        } else {
                            "Format kertas A4 Vertikal (210 × 297 mm) lengkap dengan Kop Surat KUA Donorojo, rekap 8 desa, tabel rincian, dan tanda tangan Kepala KUA."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Saved Kepala KUA Signer Card (linked to persistent settings)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Badge,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = reportConfig.signerTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = reportConfig.signerName.ifBlank { "Belum diatur (ketuk Ubah)" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (reportConfig.signerNip.isNotBlank()) {
                                    "NIP. ${reportConfig.signerNip}"
                                } else {
                                    "NIP belum diisi"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(
                        onClick = onOpenHeadOfKuaSettings,
                        modifier = Modifier.testTag("btn_edit_kepala_kua_from_report")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ubah")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Primary Action: Direct Download PDF A4
            Button(
                onClick = {
                    if (isGenerating) return@Button
                    isGenerating = true
                    scope.launch {
                        val downloadNow = System.currentTimeMillis()
                        val fileName = MonthlyReportPdfGenerator.suggestedFileName(reportConfig)
                        val saveResult = withContext(Dispatchers.IO) {
                            try {
                                val bytes = MonthlyReportPdfGenerator.buildPdfBytes(
                                    context = context,
                                    config = reportConfig,
                                    villages = villages,
                                    allSchedules = schedules,
                                    downloadTimestampMillis = downloadNow
                                )
                                MonthlyReportPdfGenerator.saveToDownloads(context, bytes, fileName)
                            } catch (e: Exception) {
                                PdfSaveResult.Failure(e.localizedMessage ?: "Gagal membuat PDF A4.")
                            }
                        }
                        isGenerating = false
                        when (saveResult) {
                            is PdfSaveResult.SavedToDownloads -> {
                                onPostMessage("PDF A4 tersimpan di folder Download: ${saveResult.fileName}")
                                onDismiss()
                            }

                            PdfSaveResult.RequiresDocumentPicker -> {
                                createDocumentLauncher.launch(fileName)
                            }

                            is PdfSaveResult.Failure -> {
                                onPostMessage(saveResult.message)
                            }
                        }
                    }
                },
                enabled = !isGenerating,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_download_monthly_pdf_a4")
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Menyiapkan PDF A4...")
                } else {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_download_pdf_a4),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Secondary Actions: Print Direct A4 & Save to Custom Folder
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (isGenerating) return@OutlinedButton
                        val printResult = MonthlyReportPdfGenerator.launchA4PrintJob(
                            context = context,
                            config = reportConfig,
                            villages = villages,
                            allSchedules = schedules
                        )
                        printResult.onFailure { err ->
                            onPostMessage(err.localizedMessage ?: "Gagal membuka pratinjau cetak A4.")
                        }
                    },
                    enabled = !isGenerating,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_print_monthly_a4")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Print,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_print_a4))
                }

                OutlinedButton(
                    onClick = {
                        if (isGenerating) return@OutlinedButton
                        createDocumentLauncher.launch(
                            MonthlyReportPdfGenerator.suggestedFileName(reportConfig)
                        )
                    },
                    enabled = !isGenerating,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_save_as_monthly_pdf")
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_save_as_folder))
                }
            }
        }
    }
}

@Composable
private fun ReportMiniStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
