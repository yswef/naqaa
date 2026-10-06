package com.naqaa.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.naqaa.app.R
import com.naqaa.app.report.ReportPdf
import com.naqaa.app.report.ReportText
import com.naqaa.app.ui.AppViewModel
import com.naqaa.app.ui.UiState
import com.naqaa.app.util.LocaleX
import com.naqaa.app.util.TimeX
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * The weekly report on screen.
 *
 * The same numbers can leave the application as a PDF, and only when the user picks a
 * destination for it. The file carries aggregates, the risk band and three suggestions; it
 * carries no note, no time and no identifier.
 */
@Composable
fun ReportScreen(viewModel: AppViewModel, state: UiState) {
    val context = LocalContext.current
    val language = state.preferences.language
    val display = LocaleX.displayLocale(language)
    val zone = ZoneId.systemDefault()
    val report = state.report
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ScreenTitle(
            title = stringResource(R.string.report_title),
            subtitle = TimeX.clockOf(report.week.from, zone, display) + " - " + TimeX.clockOf(report.week.to, zone, display)
        )

        SectionCard(title = stringResource(R.string.report_risk)) {
            StatLine(stringResource(R.string.report_risk), ReportText.risk(context, report.risk))
            StatLine(stringResource(R.string.report_score), report.score.toString())
            ValueLine(stringResource(R.string.report_streak, report.streak.current.toString()))
            ValueLine(stringResource(R.string.report_best_streak, report.streak.best.toString()))
            ValueLine(stringResource(R.string.report_clean_days, report.streak.cleanDays.toString()))
            StatLine(stringResource(R.string.progress_points), report.points.toString())
        }

        SectionCard(title = stringResource(R.string.report_week)) {
            StatLine(
                stringResource(R.string.report_lapses_week),
                stringResource(R.string.report_compare, report.week.lapses, report.previous.lapses)
            )
            StatLine(
                stringResource(R.string.report_resisted_week),
                stringResource(R.string.report_compare, report.week.resisted, report.previous.resisted)
            )
            StatLine(stringResource(R.string.report_logged), report.week.logged.toString())
            val hour = report.week.riskiestHour()
            if (hour != null) {
                ValueLine(stringResource(R.string.report_riskiest_hour, TimeX.clock(hour * 60, display)))
            }
            report.week.topTrigger()?.let {
                ValueLine(stringResource(R.string.report_riskiest_trigger, ReportText.trigger(context, it)))
            }
            report.week.topPlace()?.let {
                ValueLine(stringResource(R.string.report_riskiest_place, ReportText.place(context, it)))
            }
            report.week.topFeeling()?.let {
                ValueLine(stringResource(R.string.report_riskiest_feeling, ReportText.feeling(context, it)))
            }
        }

        SectionCard(title = stringResource(R.string.report_adherence)) {
            ValueLine(stringResource(R.string.report_prayer_days, report.week.prayerDays.toString()))
            ValueLine(stringResource(R.string.report_adhkar_days, report.week.adhkarDays.toString()))
            ValueLine(stringResource(R.string.report_quran_days, report.week.quranDays.toString()))
            ValueLine(stringResource(R.string.report_plan_days, report.week.planDays.toString()))
        }

        SectionCard(title = stringResource(R.string.report_recommendations)) {
            report.recommendations.forEachIndexed { index, recommendation ->
                Text(
                    text = "${index + 1}. " + ReportText.recommendation(context, recommendation),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        SectionCard(title = stringResource(R.string.report_disclaimer)) {
            Text(text = ReportPdf.DISCLAIMER, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = {
                scope.launch {
                    val file = withContext(Dispatchers.IO) { ReportPdf.write(context, report, language) }
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".reports", file)
                    val share = Intent(Intent.ACTION_SEND)
                        .setType("application/pdf")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    withContext(Dispatchers.Main) {
                        runCatching { context.startActivity(Intent.createChooser(share, context.getString(R.string.report_share))) }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text(text = stringResource(R.string.report_export))
        }
        Spacer(Modifier.height(20.dp))
    }
}
