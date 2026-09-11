package com.naqaa.app.report

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.naqaa.app.R
import com.naqaa.app.util.LocaleX
import com.naqaa.app.util.TimeX
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Writes the weekly report as a PDF that the user chooses to share.
 *
 * The file holds the numbers, the risk band, the three recommendations and the fixed
 * disclaimer. It never holds the journal notes, the trusted contact, the time of a lapse
 * or any identifier of the device: only aggregate values leave the application, and only
 * when the user picks a destination for the file.
 */
object ReportPdf {

    const val DISCLAIMER = "هذا التقرير نصيحة عامة مبنية على ما سجلته داخل التطبيق على هذا الجهاز، وليس تشخيصا طبيا أو نفسيا. لا يرسل التطبيق أي شيء إلى أي جهة."

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 44f

    fun write(context: Context, report: Report, language: String): File {
        val localized = LocaleX.localized(context, language)
        val display = LocaleX.displayLocale(language)
        val zone = ZoneId.systemDefault()
        val digits = Locale.US
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        val canvas = page.canvas

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
            color = localized.getColor(R.color.brand)
        }
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f
            textAlign = Paint.Align.RIGHT
        }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            textAlign = Paint.Align.RIGHT
            color = 0xFF555555.toInt()
        }

        var y = MARGIN
        fun line(paint: Paint, text: String, gap: Float = 8f) {
            canvas.drawText(text, PAGE_WIDTH - MARGIN, y, paint)
            y += paint.textSize + gap
        }

        line(title, localized.getString(R.string.report_title), gap = 14f)
        line(muted, localized.getString(R.string.report_range, TimeX.clockOf(report.week.from, zone, display), TimeX.clockOf(report.week.to, zone, display)), gap = 16f)

        line(heading, localized.getString(R.string.report_summary), gap = 10f)
        line(body, localized.getString(R.string.report_streak, number(report.streak.current, digits)))
        line(body, localized.getString(R.string.report_best_streak, number(report.streak.best, digits)))
        line(body, localized.getString(R.string.report_clean_days, number(report.streak.cleanDays, digits)))
        line(body, localized.getString(R.string.report_lapses, number(report.week.lapses, digits), number(report.previous.lapses, digits)))
        line(body, localized.getString(R.string.report_resisted, number(report.week.resisted, digits), number(report.previous.resisted, digits)), gap = 16f)

        line(heading, localized.getString(R.string.report_adherence), gap = 10f)
        line(body, localized.getString(R.string.report_prayer_days, number(report.week.prayerDays, digits)))
        line(body, localized.getString(R.string.report_adhkar_days, number(report.week.adhkarDays, digits)))
        line(body, localized.getString(R.string.report_quran_days, number(report.week.quranDays, digits)))
        line(body, localized.getString(R.string.report_plan_days, number(report.week.planDays, digits)), gap = 16f)

        line(heading, localized.getString(R.string.report_risk), gap = 10f)
        line(body, ReportText.risk(localized, report.risk) + " (" + number(report.score, digits) + ")")
        val hour = report.week.riskiestHour()
        if (hour != null) {
            line(body, localized.getString(R.string.report_riskiest_hour, TimeX.clock(hour * 60, display)))
        }
        report.week.topTrigger()?.let { line(body, localized.getString(R.string.report_riskiest_trigger, ReportText.trigger(localized, it))) }
        report.week.topPlace()?.let { line(body, localized.getString(R.string.report_riskiest_place, ReportText.place(localized, it))) }
        report.week.topFeeling()?.let { line(body, localized.getString(R.string.report_riskiest_feeling, ReportText.feeling(localized, it))) }
        y += 8f

        line(heading, localized.getString(R.string.report_recommendations), gap = 10f)
        report.recommendations.forEachIndexed { index, recommendation ->
            line(body, "${number(index + 1, digits)}. " + ReportText.recommendation(localized, recommendation))
        }
        y += 8f

        line(heading, localized.getString(R.string.report_disclaimer), gap = 10f)
        wrap(DISCLAIMER, muted, canvas, y)
        y += 60f

        val footer = localized.getString(R.string.report_footer, TimeX.clockOf(LocalDate.ofInstant(Instant.now(), zone), zone, display))
        canvas.drawText(footer, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - MARGIN, muted)

        document.finishPage(page)
        val directory = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(directory, "naqaa-report-${LocalDate.now()}.pdf")
        file.outputStream().use(document::writeTo)
        document.close()
        return file
    }

    private fun wrap(text: String, paint: Paint, canvas: android.graphics.Canvas, startY: Float) {
        val usable = PAGE_WIDTH - 2 * MARGIN
        val words = text.split(' ')
        var currentLine = StringBuilder()
        var y = startY
        words.forEach { word ->
            val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(candidate) > usable && currentLine.isNotEmpty()) {
                canvas.drawText(currentLine.toString(), PAGE_WIDTH - MARGIN, y + paint.textSize, paint)
                y += paint.textSize + 4f
                currentLine = StringBuilder(word)
            } else {
                currentLine = StringBuilder(candidate)
            }
        }
        if (currentLine.isNotEmpty()) {
            canvas.drawText(currentLine.toString(), PAGE_WIDTH - MARGIN, y + paint.textSize, paint)
        }
    }

    private fun number(value: Int, locale: Locale): String = String.format(locale, "%d", value)
}
