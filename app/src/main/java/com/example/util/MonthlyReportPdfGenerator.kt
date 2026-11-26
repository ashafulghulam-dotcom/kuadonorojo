package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import android.text.TextPaint
import com.example.R
import com.example.data.LocationType
import com.example.data.MarriageSchedule
import com.example.data.ValidationStatus
import com.example.data.Village
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MonthlyReportConfig(
    val year: Int,
    val month: Int, // 1..12
    val villageCode: String? = null,
    val villageLabel: String = "Semua 8 Desa",
    val onlyVerifiedOrDone: Boolean = false,
    val signerTitle: String = "Kepala KUA Donorojo",
    val signerName: String = "",
    val signerNip: String = ""
)

data class MonthlyReportStats(
    val total: Int,
    val atKua: Int,
    val outsideKua: Int,
    val verifiedOrDone: Int,
    val pendingOrRevision: Int,
    val cancelled: Int
)

data class VillageReportRow(
    val villageName: String,
    val total: Int,
    val atKua: Int,
    val outsideKua: Int,
    val verifiedOrDone: Int,
    val pendingOrRevision: Int,
    val cancelled: Int
)

sealed interface PdfSaveResult {
    data class SavedToDownloads(val fileName: String) : PdfSaveResult
    data object RequiresDocumentPicker : PdfSaveResult
    data class Failure(val message: String) : PdfSaveResult
}

object MonthlyReportPdfGenerator {
    // ISO A4 Vertical (Portrait) in PostScript points (72 dpi): 210mm x 297mm
    const val A4_WIDTH_PT = 595
    const val A4_HEIGHT_PT = 842

    private const val MARGIN_X = 36f
    private const val MARGIN_TOP = 32f
    private const val MARGIN_BOTTOM = 36f
    private const val CONTENT_WIDTH = A4_WIDTH_PT - (MARGIN_X * 2f) // 523f
    private const val FOOTER_Y = A4_HEIGHT_PT - 20f
    private const val MAX_BODY_Y = A4_HEIGHT_PT - MARGIN_BOTTOM - 16f

    // Table column widths (sum = 523f)
    private val COL_WIDTHS = floatArrayOf(24f, 68f, 146f, 94f, 117f, 74f)
    private val COL_HEADERS = arrayOf(
        "No",
        "Tgl & Jam",
        "No. Daftar & Pengantin",
        "Desa & Modin",
        "Lokasi & Penghulu",
        "Status"
    )

    val INDONESIAN_MONTHS: List<String> = listOf(
        "Januari",
        "Februari",
        "Maret",
        "April",
        "Mei",
        "Juni",
        "Juli",
        "Agustus",
        "September",
        "Oktober",
        "November",
        "Desember"
    )

    fun monthName(month: Int): String =
        INDONESIAN_MONTHS.getOrElse((month - 1).coerceIn(0, 11)) { "Januari" }

    fun formatDownloadDateShort(timestampMillis: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date(timestampMillis))

    fun formatDownloadDateFull(timestampMillis: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(Date(timestampMillis))

    fun periodPrefix(year: Int, month: Int): String =
        String.format(Locale.US, "%04d-%02d", year, month)

    fun suggestedFileName(config: MonthlyReportConfig): String {
        val period = periodPrefix(config.year, config.month)
        val villageSuffix = if (config.villageCode.isNullOrBlank()) {
            ""
        } else {
            "_" + config.villageCode.replace(Regex("[^a-zA-Z0-9_-]"), "")
        }
        return "Laporan_Bulanan_KUA_Donorojo_${period}${villageSuffix}.pdf"
    }

    fun filterSchedules(
        allSchedules: List<MarriageSchedule>,
        config: MonthlyReportConfig
    ): List<MarriageSchedule> {
        val prefix = periodPrefix(config.year, config.month)
        return allSchedules.filter { item ->
            val matchesMonth = item.weddingDate.startsWith(prefix)
            val matchesVillage = config.villageCode.isNullOrBlank() || item.villageId == config.villageCode
            val st = item.resolvedStatus()
            val matchesStatus = !config.onlyVerifiedOrDone ||
                st == ValidationStatus.TERVERIFIKASI ||
                st == ValidationStatus.SELESAI
            matchesMonth && matchesVillage && matchesStatus
        }.sortedWith(
            compareBy<MarriageSchedule> { it.weddingDate }
                .thenBy { it.weddingTime }
                .thenBy { it.registrationNumber }
        )
    }

    fun computeStats(schedules: List<MarriageSchedule>): MonthlyReportStats {
        var atKua = 0
        var outsideKua = 0
        var verifiedOrDone = 0
        var pendingOrRevision = 0
        var cancelled = 0

        schedules.forEach { item ->
            when (item.resolvedLocationType()) {
                LocationType.KUA -> atKua++
                LocationType.LUAR_KUA -> outsideKua++
            }
            when (item.resolvedStatus()) {
                ValidationStatus.TERVERIFIKASI, ValidationStatus.SELESAI -> verifiedOrDone++
                ValidationStatus.DIBATALKAN -> cancelled++
                ValidationStatus.MENUNGGU,
                ValidationStatus.VERIFIKASI_BERKAS,
                ValidationStatus.PERLU_REVISI -> pendingOrRevision++
            }
        }
        return MonthlyReportStats(
            total = schedules.size,
            atKua = atKua,
            outsideKua = outsideKua,
            verifiedOrDone = verifiedOrDone,
            pendingOrRevision = pendingOrRevision,
            cancelled = cancelled
        )
    }

    fun computeVillageRecap(
        villages: List<Village>,
        schedules: List<MarriageSchedule>
    ): List<VillageReportRow> {
        return villages.map { village ->
            val vSchedules = schedules.filter { it.villageId == village.code }
            val stats = computeStats(vSchedules)
            VillageReportRow(
                villageName = village.name.removePrefix("Desa "),
                total = stats.total,
                atKua = stats.atKua,
                outsideKua = stats.outsideKua,
                verifiedOrDone = stats.verifiedOrDone,
                pendingOrRevision = stats.pendingOrRevision,
                cancelled = stats.cancelled
            )
        }
    }

    fun buildPdfBytes(
        context: Context,
        config: MonthlyReportConfig,
        villages: List<Village>,
        allSchedules: List<MarriageSchedule>,
        downloadTimestampMillis: Long = System.currentTimeMillis()
    ): ByteArray {
        val filtered = filterSchedules(allSchedules, config)
        val stats = computeStats(filtered)
        val villageRows = if (config.villageCode.isNullOrBlank() && villages.isNotEmpty()) {
            computeVillageRecap(villages, filtered)
        } else {
            emptyList()
        }

        val document = PdfDocument()
        try {
            val renderer = A4ReportRenderer(
                context = context,
                document = document,
                config = config,
                stats = stats,
                villageRows = villageRows,
                schedules = filtered,
                downloadTimestampMillis = downloadTimestampMillis
            )
            renderer.renderAllPages()

            val out = ByteArrayOutputStream()
            document.writeTo(out)
            return out.toByteArray()
        } finally {
            document.close()
        }
    }

    fun saveToDownloads(
        context: Context,
        pdfBytes: ByteArray,
        fileName: String
    ): PdfSaveResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return PdfSaveResult.RequiresDocumentPicker
        }
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return PdfSaveResult.Failure("Gagal membuat file PDF di folder Download.")

            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(pdfBytes)
                stream.flush()
            } ?: return PdfSaveResult.Failure("Tidak dapat menulis ke folder Download.")

            val finalizeValues = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(uri, finalizeValues, null, null)
            PdfSaveResult.SavedToDownloads(fileName)
        } catch (e: Exception) {
            PdfSaveResult.Failure(e.localizedMessage ?: "Gagal menyimpan laporan PDF.")
        }
    }

    fun writeToUri(
        context: Context,
        uri: Uri,
        pdfBytes: ByteArray
    ): Result<Unit> {
        return runCatching {
            val out: OutputStream = context.contentResolver.openOutputStream(uri)
                ?: error("Gagal membuka lokasi penyimpanan PDF.")
            out.use { stream ->
                stream.write(pdfBytes)
                stream.flush()
            }
        }
    }

    fun launchA4PrintJob(
        context: Context,
        config: MonthlyReportConfig,
        villages: List<Village>,
        allSchedules: List<MarriageSchedule>
    ): Result<Unit> {
        return runCatching {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                ?: error("Layanan cetak Android tidak tersedia di perangkat ini.")

            val jobName = suggestedFileName(config).removeSuffix(".pdf")
            val printAttributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()

            val adapter = object : PrintDocumentAdapter() {
                private var cachedPdfBytes: ByteArray? = null

                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onLayoutCancelled()
                        return
                    }
                    try {
                        cachedPdfBytes = buildPdfBytes(
                            context = context,
                            config = config,
                            villages = villages,
                            allSchedules = allSchedules,
                            downloadTimestampMillis = System.currentTimeMillis()
                        )
                        val info = PrintDocumentInfo.Builder(suggestedFileName(config))
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                            .build()
                        callback.onLayoutFinished(info, true)
                    } catch (e: Exception) {
                        callback.onLayoutFailed(e.localizedMessage ?: "Gagal menyiapkan dokumen A4.")
                    }
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onWriteCancelled()
                        return
                    }
                    try {
                        val bytes = cachedPdfBytes
                            ?: buildPdfBytes(
                                context = context,
                                config = config,
                                villages = villages,
                                allSchedules = allSchedules,
                                downloadTimestampMillis = System.currentTimeMillis()
                            )
                        FileOutputStream(destination.fileDescriptor).use { fos ->
                            fos.write(bytes)
                            fos.flush()
                        }
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback.onWriteFailed(e.localizedMessage ?: "Gagal menulis dokumen cetak.")
                    }
                }
            }

            printManager.print(jobName, adapter, printAttributes)
        }
    }

    private class A4ReportRenderer(
        private val context: Context,
        private val document: PdfDocument,
        private val config: MonthlyReportConfig,
        private val stats: MonthlyReportStats,
        private val villageRows: List<VillageReportRow>,
        private val schedules: List<MarriageSchedule>,
        downloadTimestampMillis: Long
    ) {
        private val emeraldDark = Color.rgb(15, 93, 70)
        private val emeraldSoft = Color.rgb(235, 246, 241)
        private val slateText = Color.rgb(30, 41, 59)
        private val mutedText = Color.rgb(100, 116, 139)
        private val borderGray = Color.rgb(203, 213, 208)
        private val zebraBg = Color.rgb(248, 250, 249)

        private val kopLine1Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        private val kopLine2Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        private val kopLine3Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDark
            textSize = 12f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        private val kopSubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedText
            textSize = 7.8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        private val reportTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        private val reportSubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = slateText
            textSize = 8.2f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        private val sectionTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDark
            textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val kpiValuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDark
            textSize = 13f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        private val kpiLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedText
            textSize = 7.2f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        private val tableHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 7.8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val cellBoldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = slateText
            textSize = 7.6f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = slateText
            textSize = 7.4f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        private val cellMutedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedText
            textSize = 6.9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        private val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedText
            textSize = 7.2f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        private val signaturePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 8.4f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        private val signatureBoldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 8.6f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = borderGray
            strokeWidth = 0.6f
        }
        private val thickRulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.BLACK
            strokeWidth = 1.8f
        }
        private val thinRulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.BLACK
            strokeWidth = 0.6f
        }
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        private var pageNumber = 0
        private var currentPage: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var cursorY = MARGIN_TOP

        private val periodTitle: String = "${monthName(config.month)} ${config.year}"
        private val downloadDateShort: String = formatDownloadDateShort(downloadTimestampMillis)
        private val downloadDateFull: String = formatDownloadDateFull(downloadTimestampMillis)

        fun renderAllPages() {
            startNewPage(isContinuation = false)
            drawOfficialLetterhead()
            drawReportHeader()
            drawKpiSummaryBoxes()

            if (villageRows.isNotEmpty()) {
                drawVillageRecapTable()
            }

            drawScheduleTableSection()
            drawSignatureBlock()
            finishCurrentPage()
        }

        private fun startNewPage(isContinuation: Boolean) {
            finishCurrentPage()
            pageNumber++
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, pageNumber).create()
            val page = document.startPage(pageInfo)
            currentPage = page
            canvas = page.canvas

            // Pure white page background
            fillPaint.color = Color.WHITE
            page.canvas.drawRect(0f, 0f, A4_WIDTH_PT.toFloat(), A4_HEIGHT_PT.toFloat(), fillPaint)

            cursorY = MARGIN_TOP
            if (isContinuation) {
                drawContinuationHeader()
            }
        }

        private fun finishCurrentPage() {
            val page = currentPage ?: return
            val c = canvas ?: return
            drawPageFooter(c, pageNumber)
            document.finishPage(page)
            currentPage = null
            canvas = null
        }

        private fun drawPageFooter(c: Canvas, pageNum: Int) {
            c.drawLine(MARGIN_X, FOOTER_Y - 10f, A4_WIDTH_PT - MARGIN_X, FOOTER_Y - 10f, strokePaint)
            footerPaint.textAlign = Paint.Align.LEFT
            c.drawText(
                "Laporan Bulanan KUA Donorojo • $periodTitle • Tanggal Unduh: $downloadDateShort",
                MARGIN_X,
                FOOTER_Y,
                footerPaint
            )
            footerPaint.textAlign = Paint.Align.RIGHT
            c.drawText(
                "Halaman $pageNum",
                A4_WIDTH_PT - MARGIN_X,
                FOOTER_Y,
                footerPaint
            )
        }

        private fun drawContinuationHeader() {
            val c = canvas ?: return
            footerPaint.textAlign = Paint.Align.LEFT
            c.drawText(
                "LANJUTAN LAPORAN BULANAN KUA DONOROJO — PERIODE ${periodTitle.uppercase(Locale("id", "ID"))}",
                MARGIN_X,
                cursorY + 6f,
                sectionTitlePaint
            )
            cursorY += 12f
            c.drawLine(MARGIN_X, cursorY, A4_WIDTH_PT - MARGIN_X, cursorY, thinRulePaint)
            cursorY += 10f
        }

        private fun drawOfficialLetterhead() {
            val c = canvas ?: return
            val topY = cursorY

            // Official KUA logo on the left
            val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_logo_kua_resmi)
            if (logoBitmap != null) {
                val logoRect = RectF(MARGIN_X + 2f, topY + 2f, MARGIN_X + 48f, topY + 46f)
                c.drawBitmap(logoBitmap, null, logoRect, bitmapPaint)
            }

            // Centered kop surat text shifted slightly right so it balances visually with logo
            val centerX = MARGIN_X + 26f + ((CONTENT_WIDTH - 26f) / 2f)
            c.drawText("KEMENTERIAN AGAMA REPUBLIK INDONESIA", centerX, topY + 12f, kopLine1Paint)
            c.drawText("KANTOR KEMENTERIAN AGAMA KABUPATEN JEPARA", centerX, topY + 24f, kopLine2Paint)
            c.drawText("KANTOR URUSAN AGAMA (KUA) DONOROJO", centerX, topY + 38f, kopLine3Paint)
            c.drawText(
                "Wilayah Pelayanan 8 Desa • Donorojo, Kabupaten Jepara, Jawa Tengah",
                centerX,
                topY + 49f,
                kopSubPaint
            )

            cursorY = topY + 56f
            c.drawLine(MARGIN_X, cursorY, A4_WIDTH_PT - MARGIN_X, cursorY, thickRulePaint)
            cursorY += 2.8f
            c.drawLine(MARGIN_X, cursorY, A4_WIDTH_PT - MARGIN_X, cursorY, thinRulePaint)
            cursorY += 14f
        }

        private fun drawReportHeader() {
            val c = canvas ?: return
            val centerX = A4_WIDTH_PT / 2f
            c.drawText(
                "LAPORAN BULANAN JADWAL & PELAKSANAAN AKAD NIKAH",
                centerX,
                cursorY,
                reportTitlePaint
            )
            cursorY += 13f
            val statusScope = if (config.onlyVerifiedOrDone) {
                "Terverifikasi & Selesai"
            } else {
                "Semua Status"
            }
            c.drawText(
                "Periode: $periodTitle  •  Wilayah: ${config.villageLabel}  •  Filter: $statusScope",
                centerX,
                cursorY,
                reportSubPaint
            )
            cursorY += 11f
            c.drawText(
                "Tanggal Laporan: $downloadDateFull",
                centerX,
                cursorY,
                kopSubPaint
            )
            cursorY += 13f
        }

        private fun drawKpiSummaryBoxes() {
            val c = canvas ?: return
            val items = listOf(
                "Total Jadwal" to stats.total.toString(),
                "Balai KUA" to stats.atKua.toString(),
                "Luar KUA" to stats.outsideKua.toString(),
                "Terverifikasi / Selesai" to stats.verifiedOrDone.toString(),
                "Proses / Batal" to "${stats.pendingOrRevision} / ${stats.cancelled}"
            )
            val gap = 6f
            val boxW = (CONTENT_WIDTH - gap * (items.size - 1)) / items.size
            val boxH = 38f

            items.forEachIndexed { idx, (label, value) ->
                val left = MARGIN_X + idx * (boxW + gap)
                val rect = RectF(left, cursorY, left + boxW, cursorY + boxH)
                fillPaint.color = if (idx == 0) emeraldSoft else Color.WHITE
                c.drawRoundRect(rect, 4f, 4f, fillPaint)
                c.drawRoundRect(rect, 4f, 4f, strokePaint)

                val cx = left + boxW / 2f
                c.drawText(value, cx, cursorY + 18f, kpiValuePaint)
                c.drawText(label, cx, cursorY + 31f, kpiLabelPaint)
            }
            cursorY += boxH + 14f
        }

        private fun drawVillageRecapTable() {
            val c = canvas ?: return
            c.drawText("A. REKAPITULASI PER DESA (8 DESA WILAYAH KUA DONOROJO)", MARGIN_X, cursorY, sectionTitlePaint)
            cursorY += 7f

            val widths = floatArrayOf(24f, 159f, 56f, 56f, 56f, 64f, 58f, 50f) // sum = 523f
            val headers = arrayOf("No", "Nama Desa", "Total", "Balai KUA", "Luar KUA", "Sah/Selesai", "Proses", "Batal")
            val headerH = 18f
            val rowH = 15.5f

            // Header bar
            fillPaint.color = emeraldDark
            c.drawRect(MARGIN_X, cursorY, MARGIN_X + CONTENT_WIDTH, cursorY + headerH, fillPaint)
            var x = MARGIN_X
            headers.forEachIndexed { i, title ->
                tableHeaderPaint.textAlign = if (i == 1) Paint.Align.LEFT else Paint.Align.CENTER
                val tx = if (i == 1) x + 5f else x + widths[i] / 2f
                c.drawText(title, tx, cursorY + 12f, tableHeaderPaint)
                c.drawRect(x, cursorY, x + widths[i], cursorY + headerH, strokePaint)
                x += widths[i]
            }
            cursorY += headerH

            // Village rows
            villageRows.forEachIndexed { idx, row ->
                fillPaint.color = if (idx % 2 == 1) zebraBg else Color.WHITE
                c.drawRect(MARGIN_X, cursorY, MARGIN_X + CONTENT_WIDTH, cursorY + rowH, fillPaint)
                val vals = arrayOf(
                    (idx + 1).toString(),
                    row.villageName,
                    row.total.toString(),
                    row.atKua.toString(),
                    row.outsideKua.toString(),
                    row.verifiedOrDone.toString(),
                    row.pendingOrRevision.toString(),
                    row.cancelled.toString()
                )
                var rx = MARGIN_X
                vals.forEachIndexed { colIdx, text ->
                    val paint = if (colIdx == 2) cellBoldPaint else cellPaint
                    paint.textAlign = if (colIdx == 1) Paint.Align.LEFT else Paint.Align.CENTER
                    val tx = if (colIdx == 1) rx + 5f else rx + widths[colIdx] / 2f
                    c.drawText(text, tx, cursorY + 10.8f, paint)
                    c.drawRect(rx, cursorY, rx + widths[colIdx], cursorY + rowH, strokePaint)
                    rx += widths[colIdx]
                }
                cursorY += rowH
            }

            // Total footer row
            fillPaint.color = emeraldSoft
            c.drawRect(MARGIN_X, cursorY, MARGIN_X + CONTENT_WIDTH, cursorY + rowH + 1f, fillPaint)
            val totals = arrayOf(
                "",
                "JUMLAH TOTAL",
                stats.total.toString(),
                stats.atKua.toString(),
                stats.outsideKua.toString(),
                stats.verifiedOrDone.toString(),
                stats.pendingOrRevision.toString(),
                stats.cancelled.toString()
            )
            var txPos = MARGIN_X
            totals.forEachIndexed { colIdx, text ->
                cellBoldPaint.textAlign = if (colIdx == 1) Paint.Align.LEFT else Paint.Align.CENTER
                val tx = if (colIdx == 1) txPos + 5f else txPos + widths[colIdx] / 2f
                c.drawText(text, tx, cursorY + 11.2f, cellBoldPaint)
                c.drawRect(txPos, cursorY, txPos + widths[colIdx], cursorY + rowH + 1f, strokePaint)
                txPos += widths[colIdx]
            }
            cursorY += rowH + 15f
        }

        private fun drawScheduleTableSection() {
            val sectionLetter = if (villageRows.isNotEmpty()) "B" else "A"
            if (cursorY + 46f > MAX_BODY_Y) {
                startNewPage(isContinuation = true)
            }
            val c = canvas ?: return
            c.drawText(
                "$sectionLetter. DAFTAR RINCIAN JADWAL AKAD NIKAH ($periodTitle)",
                MARGIN_X,
                cursorY,
                sectionTitlePaint
            )
            cursorY += 7f
            drawScheduleTableHeader()

            if (schedules.isEmpty()) {
                val emptyH = 34f
                fillPaint.color = zebraBg
                c.drawRect(MARGIN_X, cursorY, MARGIN_X + CONTENT_WIDTH, cursorY + emptyH, fillPaint)
                c.drawRect(MARGIN_X, cursorY, MARGIN_X + CONTENT_WIDTH, cursorY + emptyH, strokePaint)
                cellMutedPaint.textAlign = Paint.Align.CENTER
                c.drawText(
                    "Laporan Nihil — Tidak ada jadwal pernikahan tercatat untuk periode $periodTitle.",
                    MARGIN_X + CONTENT_WIDTH / 2f,
                    cursorY + 20f,
                    cellMutedPaint
                )
                cellMutedPaint.textAlign = Paint.Align.LEFT
                cursorY += emptyH + 16f
                return
            }

            schedules.forEachIndexed { index, schedule ->
                drawScheduleRow(index = index + 1, schedule = schedule)
            }
            cursorY += 14f
        }

        private fun drawScheduleTableHeader() {
            val c = canvas ?: return
            val headerH = 20f
            fillPaint.color = emeraldDark
            c.drawRect(MARGIN_X, cursorY, MARGIN_X + CONTENT_WIDTH, cursorY + headerH, fillPaint)

            var x = MARGIN_X
            COL_HEADERS.forEachIndexed { idx, title ->
                tableHeaderPaint.textAlign = if (idx == 0) Paint.Align.CENTER else Paint.Align.LEFT
                val tx = if (idx == 0) x + COL_WIDTHS[idx] / 2f else x + 4f
                c.drawText(title, tx, cursorY + 13f, tableHeaderPaint)
                c.drawRect(x, cursorY, x + COL_WIDTHS[idx], cursorY + headerH, strokePaint)
                x += COL_WIDTHS[idx]
            }
            cursorY += headerH
        }

        private fun drawScheduleRow(index: Int, schedule: MarriageSchedule) {
            val padX = 4f
            val padY = 4.5f
            val lineH = 9.5f

            val shortDate = formatCompactIndoDate(schedule.weddingDate)
            val timeLine = if (schedule.weddingTime.isNotBlank()) "${schedule.weddingTime} WIB" else "-"

            val col0 = listOf(CellLine(index.toString(), cellPaint, center = true))
            val col1 = buildList {
                wrapText(shortDate, cellBoldPaint, COL_WIDTHS[1] - padX * 2).forEach {
                    add(CellLine(it, cellBoldPaint))
                }
                add(CellLine(timeLine, cellMutedPaint))
            }
            val col2 = buildList {
                wrapText("${schedule.groomName} & ${schedule.brideName}", cellBoldPaint, COL_WIDTHS[2] - padX * 2).forEach {
                    add(CellLine(it, cellBoldPaint))
                }
                if (schedule.registrationNumber.isNotBlank()) {
                    wrapText(schedule.registrationNumber, cellMutedPaint, COL_WIDTHS[2] - padX * 2).forEach {
                        add(CellLine(it, cellMutedPaint))
                    }
                }
            }
            val col3 = buildList {
                val villageClean = schedule.villageName.ifBlank { "-" }
                wrapText(villageClean, cellBoldPaint, COL_WIDTHS[3] - padX * 2).forEach {
                    add(CellLine(it, cellBoldPaint))
                }
                val modinText = if (schedule.modinName.isNotBlank()) "Modin: ${schedule.modinName}" else "Modin Desa"
                wrapText(modinText, cellMutedPaint, COL_WIDTHS[3] - padX * 2).forEach {
                    add(CellLine(it, cellMutedPaint))
                }
            }
            val col4 = buildList {
                val locType = schedule.resolvedLocationType().label
                val locFull = if (schedule.locationAddress.isNotBlank()) {
                    "$locType • ${schedule.locationAddress}"
                } else {
                    locType
                }
                wrapText(locFull, cellPaint, COL_WIDTHS[4] - padX * 2).forEach {
                    add(CellLine(it, cellPaint))
                }
                val penghulu = schedule.penghuluName.ifBlank { "Belum ditunjuk" }
                wrapText("Penghulu: $penghulu", cellMutedPaint, COL_WIDTHS[4] - padX * 2).forEach {
                    add(CellLine(it, cellMutedPaint))
                }
            }
            val col5 = buildList {
                val statusLabel = schedule.resolvedStatus().shortLabel
                wrapText(statusLabel, cellBoldPaint, COL_WIDTHS[5] - padX * 2).forEach {
                    add(CellLine(it, cellBoldPaint))
                }
                val docLabel = if (schedule.documentsComplete) "Berkas Lengkap" else "Cek Berkas"
                wrapText(docLabel, cellMutedPaint, COL_WIDTHS[5] - padX * 2).forEach {
                    add(CellLine(it, cellMutedPaint))
                }
            }

            val allCols = listOf(col0, col1, col2, col3, col4, col5)
            val maxLines = allCols.maxOf { it.size }.coerceAtLeast(1)
            val rowHeight = (padY * 2f) + (maxLines * lineH)

            if (cursorY + rowHeight > MAX_BODY_Y) {
                startNewPage(isContinuation = true)
                drawScheduleTableHeader()
            }

            val c = canvas ?: return
            fillPaint.color = if (index % 2 == 0) zebraBg else Color.WHITE
            c.drawRect(MARGIN_X, cursorY, MARGIN_X + CONTENT_WIDTH, cursorY + rowHeight, fillPaint)

            var x = MARGIN_X
            allCols.forEachIndexed { colIdx, lines ->
                val colW = COL_WIDTHS[colIdx]
                var lineY = cursorY + padY + 7.2f
                lines.forEach { cellLine ->
                    cellLine.paint.textAlign = if (cellLine.center) Paint.Align.CENTER else Paint.Align.LEFT
                    val tx = if (cellLine.center) x + colW / 2f else x + padX
                    c.drawText(cellLine.text, tx, lineY, cellLine.paint)
                    lineY += lineH
                }
                c.drawRect(x, cursorY, x + colW, cursorY + rowHeight, strokePaint)
                x += colW
            }

            cursorY += rowHeight
        }

        private fun drawSignatureBlock() {
            val neededHeight = 112f
            if (cursorY + neededHeight > MAX_BODY_Y) {
                startNewPage(isContinuation = true)
            }
            val c = canvas ?: return
            val sigX = MARGIN_X + CONTENT_WIDTH - 190f
            var y = cursorY + 6f

            signaturePaint.textAlign = Paint.Align.LEFT
            signatureBoldPaint.textAlign = Paint.Align.LEFT

            c.drawText("Donorojo, $downloadDateShort", sigX, y, signaturePaint)
            y += 12f
            c.drawText("Mengetahui,", sigX, y, signaturePaint)
            y += 12f
            val titleText = config.signerTitle.trim().ifBlank { "Kepala KUA Donorojo" }
            c.drawText(titleText, sigX, y, signatureBoldPaint)

            // Space for wet signature & official KUA stamp
            y += 48f
            val displayName = config.signerName.trim().ifBlank { "( ............................................ )" }
            c.drawText(displayName, sigX, y, signatureBoldPaint)

            if (config.signerName.trim().isNotEmpty()) {
                val nameW = signatureBoldPaint.measureText(displayName).coerceAtMost(185f)
                c.drawLine(sigX, y + 2f, sigX + nameW, y + 2f, thinRulePaint)
            }

            if (config.signerNip.trim().isNotEmpty()) {
                y += 12f
                c.drawText("NIP. ${config.signerNip.trim()}", sigX, y, signaturePaint)
            }
        }

        private fun formatCompactIndoDate(isoDate: String): String {
            return try {
                val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val date = parser.parse(isoDate) ?: return isoDate
                val out = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
                out.format(date)
            } catch (_: Exception) {
                isoDate
            }
        }

        private fun wrapText(text: String, paint: TextPaint, maxWidth: Float): List<String> {
            val cleaned = text.trim()
            if (cleaned.isEmpty()) return listOf("-")
            val result = mutableListOf<String>()
            cleaned.split("\n").forEach { paragraph ->
                val words = paragraph.split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (words.isEmpty()) return@forEach
                var current = StringBuilder()
                words.forEach { word ->
                    val candidate = if (current.isEmpty()) word else "$current $word"
                    if (paint.measureText(candidate) <= maxWidth) {
                        current = StringBuilder(candidate)
                    } else {
                        if (current.isNotEmpty()) {
                            result.add(current.toString())
                        }
                        if (paint.measureText(word) <= maxWidth) {
                            current = StringBuilder(word)
                        } else {
                            // Break very long token cleanly so it never overflows column border
                            var chunk = StringBuilder()
                            word.forEach { ch ->
                                val next = chunk.toString() + ch
                                if (paint.measureText(next) <= maxWidth) {
                                    chunk.append(ch)
                                } else {
                                    if (chunk.isNotEmpty()) result.add(chunk.toString())
                                    chunk = StringBuilder(ch.toString())
                                }
                            }
                            current = chunk
                        }
                    }
                }
                if (current.isNotEmpty()) {
                    result.add(current.toString())
                }
            }
            return result.ifEmpty { listOf("-") }
        }

        private data class CellLine(
            val text: String,
            val paint: TextPaint,
            val center: Boolean = false
        )
    }
}
