package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PregnantWoman
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.formatIndonesianDate
import com.example.data.todayIsoDate
import com.example.ui.theme.StatusPendingBg
import com.example.ui.theme.StatusPendingFg
import com.example.ui.theme.StatusRevisionBg
import com.example.ui.theme.StatusRevisionFg
import com.example.ui.theme.StatusVerifiedBg
import com.example.ui.theme.StatusVerifiedFg
import com.example.util.AgeLegalTier
import com.example.util.DivorceType
import com.example.util.DukhulStatus
import com.example.util.IddahMarriageEligibility
import com.example.util.KuaLegalCalculator
import com.example.util.MenstruationStatus
import com.example.util.PersonAgeResult
import com.example.util.WaliVerdict

private enum class CalculatorTab(val label: String, val icon: ImageVector) {
    WALI("Wali Nikah", Icons.Filled.FamilyRestroom),
    IDDAH("Masa Iddah", Icons.Filled.HourglassTop),
    UMUR("Umur Catin", Icons.Filled.Cake)
}

@Composable
fun KuaCalculatorsSection(
    modifier: Modifier = Modifier
) {
    var activeCalc by remember { mutableStateOf(CalculatorTab.WALI) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Title + Segmented Sub-Tab Switcher
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Balance,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Kalkulator Hukum Nikah KUA Donorojo",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Cek Wali Nikah (Hijriyah), Masa Iddah (KHI), dan Umur Catin sekaligus",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CalculatorTab.entries.forEach { tab ->
                        val selected = activeCalc == tab
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { activeCalc = tab }
                                .testTag("calc_tab_${tab.name.lowercase()}")
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        when (activeCalc) {
            CalculatorTab.WALI -> WaliNikahCalculatorCard()
            CalculatorTab.IDDAH -> MasaIddahCalculatorCard()
            CalculatorTab.UMUR -> UmurCatinCalculatorCard()
        }
    }
}

@Composable
private fun WaliNikahCalculatorCard() {
    var parentMarriageDate by remember { mutableStateOf("2001-05-10") }
    var brideBirthDate by remember { mutableStateOf("2002-02-20") }

    val result = remember(parentMarriageDate, brideBirthDate) {
        KuaLegalCalculator.calculateWaliNikah(
            parentMarriageIso = parentMarriageDate,
            brideBirthIso = brideBirthDate
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Cek Wali Nikah (Hitungan Hijriyah)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Bandingkan tanggal nikah sah orang tua (dari Buku Nikah Ortu) dengan tanggal lahir calon pengantin perempuan secara kalender Hijriyah.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                DateSelectorField(
                    label = "Tanggal Nikah Sah Orang Tua (Buku Nikah Ortu)",
                    isoDate = parentMarriageDate,
                    hijriSubtitle = result?.parentMarriageHijri?.formatted(),
                    onDateSelected = { parentMarriageDate = it },
                    testTag = "wali_parent_marriage_date"
                )

                DateSelectorField(
                    label = "Tanggal Lahir Catin Perempuan",
                    isoDate = brideBirthDate,
                    hijriSubtitle = result?.brideBirthHijri?.formatted(),
                    onDateSelected = { brideBirthDate = it },
                    testTag = "wali_bride_birth_date"
                )
            }
        }

        if (result != null) {
            val isNasab = result.verdict == WaliVerdict.WALI_NASAB
            val bgColor = if (isNasab) StatusVerifiedBg else StatusRevisionBg
            val fgColor = if (isNasab) StatusVerifiedFg else StatusRevisionFg

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = bgColor.copy(alpha = 0.75f),
                border = BorderStroke(1.5.dp, fgColor.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wali_result_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(fgColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isNasab) Icons.Filled.Verified else Icons.Filled.Gavel,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hasil Penentuan Wali Nikah",
                                style = MaterialTheme.typography.labelSmall,
                                color = fgColor.copy(alpha = 0.85f)
                            )
                            Text(
                                text = result.verdict.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = fgColor
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CalcInfoRow(
                                label = "Nikah Ortu (Hijriyah)",
                                value = result.parentMarriageHijri.formatted()
                            )
                            CalcInfoRow(
                                label = "Lahir Catin (Hijriyah)",
                                value = result.brideBirthHijri.formatted()
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            CalcInfoRow(
                                label = "Selisih Hijriyah",
                                value = if (result.isBornBeforeMarriage) {
                                    "Lahir sebelum nikah (${kotlin.math.abs(result.totalDays)} hari)"
                                } else {
                                    "${result.hijriTotalMonths} Bulan ${result.hijriRemainingDays} Hari (${result.totalDays} hari)"
                                },
                                valueColor = fgColor
                            )
                            CalcInfoRow(
                                label = "Ketentuan Batas",
                                value = "> 6 Bulan Hijriyah = Wali Nasab"
                            )
                        }
                    }

                    Text(
                        text = result.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = fgColor
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = fgColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = result.legalBasis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MasaIddahCalculatorCard() {
    var divorceType by remember { mutableStateOf(DivorceType.CERAI_TALAK) }
    var isPregnant by remember { mutableStateOf(false) }
    var dukhulStatus by remember { mutableStateOf(DukhulStatus.BADA_DUKHUL) }
    var menstruationStatus by remember { mutableStateOf(MenstruationStatus.MASIH_HAID) }
    var divorceOrDeathDate by remember { mutableStateOf(KuaLegalCalculator.addDaysToIso(todayIsoDate(), -60)) }
    var plannedWeddingDate by remember { mutableStateOf(KuaLegalCalculator.addDaysToIso(todayIsoDate(), 45)) }

    val result = remember(
        divorceType,
        isPregnant,
        dukhulStatus,
        menstruationStatus,
        divorceOrDeathDate,
        plannedWeddingDate
    ) {
        KuaLegalCalculator.calculateMasaIddah(
            divorceType = divorceType,
            isPregnant = isPregnant,
            dukhulStatus = dukhulStatus,
            menstruationStatus = menstruationStatus,
            startDateIso = divorceOrDeathDate,
            plannedWeddingIso = plannedWeddingDate
        )
    }

    val isCeraiHidup = divorceType != DivorceType.CERAI_MATI

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Kalkulator Masa Iddah (KHI & Fikih)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Hitung masa tunggu (iddah) untuk Cerai Mati, Cerai Talak, maupun Cerai Gugat serta cek kelayakan tanggal akad nikah baru.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 1. Status Perceraian
                Text(
                    text = "1. Status Perceraian",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DivorceType.entries.forEach { type ->
                        SelectablePillOption(
                            label = type.label,
                            selected = divorceType == type,
                            onClick = { divorceType = type },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("iddah_divorce_${type.name.lowercase()}")
                        )
                    }
                }

                // 2. Keadaan Hamil
                Text(
                    text = "2. Keadaan Kehamilan",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectablePillOption(
                        label = "Tidak Hamil",
                        selected = !isPregnant,
                        onClick = { isPregnant = false },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("iddah_not_pregnant")
                    )
                    SelectablePillOption(
                        label = "Sedang Hamil",
                        selected = isPregnant,
                        onClick = { isPregnant = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("iddah_pregnant")
                    )
                }

                // 3. Ba'da Dukhul / Qabla Dukhul
                Text(
                    text = "3. Status Hubungan (Dukhul)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DukhulStatus.entries.forEach { status ->
                        SelectablePillOption(
                            label = status.label,
                            selected = dukhulStatus == status,
                            onClick = { dukhulStatus = status },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("iddah_dukhul_${status.name.lowercase()}")
                        )
                    }
                }
                if (divorceType == DivorceType.CERAI_MATI && dukhulStatus == DukhulStatus.QABLA_DUKHUL && !isPregnant) {
                    Text(
                        text = "Catatan: Pada Cerai Mati, masa iddah 4 bulan 10 hari (130 hari) tetap berlaku meskipun qabla ad-dukhul.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 4. Kondisi Haid (for Cerai Hidup + Tidak Hamil + Ba'da Dukhul)
                AnimatedVisibility(visible = isCeraiHidup && !isPregnant && dukhulStatus == DukhulStatus.BADA_DUKHUL) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "4. Kondisi Haid",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MenstruationStatus.entries.forEach { st ->
                                SelectablePillOption(
                                    label = st.label,
                                    selected = menstruationStatus == st,
                                    onClick = { menstruationStatus = st },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("iddah_haid_${st.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                DateSelectorField(
                    label = if (divorceType == DivorceType.CERAI_MATI) {
                        "Tanggal Wafat Suami (Mulai Iddah)"
                    } else {
                        "Tanggal Putus Cerai / Akta Cerai (Mulai Iddah)"
                    },
                    isoDate = divorceOrDeathDate,
                    onDateSelected = { divorceOrDeathDate = it },
                    testTag = "iddah_start_date"
                )

                DateSelectorField(
                    label = "Tanggal Rencana Nikah Baru",
                    isoDate = plannedWeddingDate,
                    onDateSelected = { plannedWeddingDate = it },
                    testTag = "iddah_planned_wedding_date"
                )
            }
        }

        if (result != null) {
            val (bgColor, fgColor, icon) = when (result.eligibility) {
                IddahMarriageEligibility.ELIGIBLE -> Triple(StatusVerifiedBg, StatusVerifiedFg, Icons.Filled.CheckCircle)
                IddahMarriageEligibility.STILL_IN_IDDAH -> Triple(StatusRevisionBg, StatusRevisionFg, Icons.Filled.EventBusy)
                IddahMarriageEligibility.WAIT_FOR_BIRTH -> Triple(StatusPendingBg, StatusPendingFg, Icons.Filled.PregnantWoman)
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = bgColor.copy(alpha = 0.78f),
                border = BorderStroke(1.5.dp, fgColor.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("iddah_result_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(fgColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hasil Cek Masa Iddah",
                                style = MaterialTheme.typography.labelSmall,
                                color = fgColor.copy(alpha = 0.85f)
                            )
                            Text(
                                text = result.verdictTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = fgColor
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CalcInfoRow("Ketentuan Masa Iddah", result.durationLabel, valueColor = fgColor)
                            if (result.iddahEndDateIso != null) {
                                CalcInfoRow("Akhir Masa Iddah", formatIndonesianDate(result.iddahEndDateIso))
                            }
                            if (result.earliestAllowedWeddingIso != null) {
                                CalcInfoRow(
                                    label = "Paling Cepat Boleh Nikah",
                                    value = formatIndonesianDate(result.earliestAllowedWeddingIso),
                                    valueColor = MaterialTheme.colorScheme.primary
                                )
                            }
                            CalcInfoRow("Rencana Tanggal Nikah", formatIndonesianDate(result.plannedWeddingIso))
                        }
                    }

                    Text(
                        text = result.explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = fgColor
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = fgColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = result.legalBasis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UmurCatinCalculatorCard() {
    var weddingDate by remember { mutableStateOf(todayIsoDate()) }
    var groomBirthDate by remember { mutableStateOf("2003-04-12") }
    var brideBirthDate by remember { mutableStateOf("2006-08-18") }

    val result = remember(groomBirthDate, brideBirthDate, weddingDate) {
        KuaLegalCalculator.calculateCoupleAge(
            groomBirthIso = groomBirthDate,
            brideBirthIso = brideBirthDate,
            weddingDateIso = weddingDate
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Kalkulator Umur Catin (Suami & Istri)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Cek usia Calon Suami dan Calon Istri secara bersamaan pada hari akad sesuai batas minimal 19 tahun (UU No. 16 Tahun 2019) & izin orang tua di bawah 21 tahun.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                DateSelectorField(
                    label = "Tanggal Rencana Akad Nikah",
                    isoDate = weddingDate,
                    onDateSelected = { weddingDate = it },
                    testTag = "age_wedding_date"
                )

                DateSelectorField(
                    label = "Tanggal Lahir Calon Suami",
                    isoDate = groomBirthDate,
                    onDateSelected = { groomBirthDate = it },
                    testTag = "age_groom_birth_date"
                )

                DateSelectorField(
                    label = "Tanggal Lahir Calon Istri",
                    isoDate = brideBirthDate,
                    onDateSelected = { brideBirthDate = it },
                    testTag = "age_bride_birth_date"
                )
            }
        }

        if (result != null) {
            val (overallBg, overallFg, overallIcon) = tierVisuals(result.overallTier)

            // Combined Couple Verdict Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = overallBg.copy(alpha = 0.78f),
                border = BorderStroke(1.5.dp, overallFg.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("age_overall_result_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(overallFg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = overallIcon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Kesimpulan Kelayakan Pasangan",
                                style = MaterialTheme.typography.labelSmall,
                                color = overallFg.copy(alpha = 0.85f)
                            )
                            Text(
                                text = result.overallTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = overallFg
                            )
                        }
                    }

                    Text(
                        text = result.overallSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = overallFg
                    )
                }
            }

            // Individual Detail Cards (Calon Suami & Calon Istri)
            PersonAgeResultCard(person = result.groom, testTag = "age_groom_result_card")
            PersonAgeResultCard(person = result.bride, testTag = "age_bride_result_card")
        }
    }
}

@Composable
private fun PersonAgeResultCard(
    person: PersonAgeResult,
    testTag: String
) {
    val (bg, fg, icon) = tierVisuals(person.tier)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = person.roleLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = bg,
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = fg,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = person.tier.badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = fg
                        )
                    }
                }
            }

            Text(
                text = if (person.isInvalidDate) "Tanggal tidak valid" else person.formattedAge(),
                style = MaterialTheme.typography.headlineSmall,
                color = fg
            )

            Text(
                text = person.tier.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = person.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (person.tier == AgeLegalTier.UNDER_19 && !person.isInvalidDate) {
                Surface(
                    color = bg.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Genap 19 tahun pada: ${formatIndonesianDate(person.turns19DateIso)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = fg,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

private fun tierVisuals(tier: AgeLegalTier): Triple<Color, Color, ImageVector> = when (tier) {
    AgeLegalTier.UNDER_19 -> Triple(StatusRevisionBg, StatusRevisionFg, Icons.Filled.Gavel)
    AgeLegalTier.AGE_19_TO_20 -> Triple(StatusPendingBg, StatusPendingFg, Icons.Filled.WarningAmber)
    AgeLegalTier.AGE_21_PLUS -> Triple(StatusVerifiedBg, StatusVerifiedFg, Icons.Filled.Verified)
}

@Composable
private fun SelectablePillOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelectorField(
    label: String,
    isoDate: String,
    hijriSubtitle: String? = null,
    onDateSelected: (String) -> Unit,
    testTag: String
) {
    var showPicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { showPicker = true }
                .testTag(testTag)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatIndonesianDate(isoDate),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!hijriSubtitle.isNullOrBlank()) {
                        Text(
                            text = "Hijriyah: $hijriSubtitle",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = "Pilih tanggal",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (showPicker) {
        val initialMillis = remember(isoDate) { KuaLegalCalculator.isoToUtcMillis(isoDate) }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = datePickerState.selectedDateMillis
                        if (selected != null) {
                            onDateSelected(KuaLegalCalculator.utcMillisToIso(selected))
                        }
                        showPicker = false
                    }
                ) {
                    Text("Pilih")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Batal")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun CalcInfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            modifier = Modifier.weight(0.55f)
        )
    }
}
