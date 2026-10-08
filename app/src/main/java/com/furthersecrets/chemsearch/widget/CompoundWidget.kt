package com.furthersecrets.chemsearch.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.furthersecrets.chemsearch.R
import com.furthersecrets.chemsearch.data.ChemicalDatabase
import com.furthersecrets.chemsearch.data.ChemicalDbCategory
import com.furthersecrets.chemsearch.data.ChemicalDbEntry
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Content shared by both widgets. */
data class CompoundOfTheDay(
    val name: String,
    val formula: String,
    val uses: String
)

internal fun ChemicalDbEntry.toWidgetCompound(): CompoundOfTheDay = CompoundOfTheDay(
    name = title,
    formula = formula.ifBlank { "—" },
    uses = summary.replace(Regex("\\s+"), " ").trim().take(110)
)

/**
 * Renders chemical formulas as plain Unicode with real subscript and
 * superscript characters (₀-₉, ⁰-⁹, ⁺, ⁻). Unlike HTML baseline-shift spans,
 * Unicode glyphs occupy normal line positions, so nothing is clipped by the
 * widget's single-line TextViews regardless of the host launcher.
 *
 * Notation handled:
 *  - digits after an element or `)`: subscripts (`C2H4O2`, `Al(OH)3`)
 *  - digits after a separator (`·`, space, `+`, `,`): coefficients (`CuSO4·5H2O`)
 *  - explicit charges: `As^3-`
 *  - trailing charges: `Al3+`, `Br-`, `NH4+`, `SO4 2-`
 */
object WidgetFormulas {
    private val SUPERSCRIPT_DIGITS: Map<Char, String> = mapOf(
        '0' to "⁰", '1' to "¹", '2' to "²", '3' to "³", '4' to "⁴",
        '5' to "⁵", '6' to "⁶", '7' to "⁷", '8' to "⁸", '9' to "⁹"
    )
    private const val SUPERSCRIPT_PLUS = '⁺'
    private const val SUPERSCRIPT_MINUS = '⁻'

    fun toSubscript(c: Char): Char = Char(0x2080 + (c - '0'))

    fun format(raw: String): String {
        val text = raw.trim()
        if (text.isBlank()) return ""
        var body = text
        var chargeDigits = ""
        var chargeSign = ""
        val signEnd = Regex("([+\\-−])$").find(text)
        if (signEnd != null) {
            chargeSign = signEnd.groupValues[1].replace('-', SUPERSCRIPT_MINUS).replace('−', SUPERSCRIPT_MINUS)
            val core = text.dropLast(1)
            val caret = core.lastIndexOf('^')
            val trailingDigits = core.takeLastWhile { it.isDigit() }
            when {
                caret >= 0 -> {
                    chargeDigits = core.substring(caret + 1)
                    body = core.substring(0, caret)
                }
                trailingDigits.isNotEmpty() -> {
                    val rest = core.dropLast(trailingDigits.length)
                    val afterSpace = rest.endsWith(" ")
                    val elementCount = rest.count { it.isUpperCase() }
                    if (afterSpace || elementCount < 2) {
                        // "SO4 2-" or a single element "Al3+": digits are the charge magnitude.
                        chargeDigits = trailingDigits
                        body = rest
                    } else {
                        // Multi-element ion like NH4+ / NO3-: digits are a subscript.
                        body = core
                    }
                }
                else -> body = core
            }
        }

        val sb = StringBuilder()
        var i = 0
        var afterSeparator = true
        while (i < body.length) {
            val c = body[i]
            when {
                c.isDigit() -> {
                    val start = i
                    while (i < body.length && body[i].isDigit()) i++
                    for (j in start until i) {
                        val d = body[j]
                        if (afterSeparator) sb.append(d) else sb.append(toSubscript(d))
                    }
                    afterSeparator = false
                }
                c == ' ' || c == '·' || c == '+' || c == '-' || c == '−' || c == ',' || c == '/' -> {
                    sb.append(c)
                    afterSeparator = true
                    i++
                }
                else -> {
                    sb.append(c)
                    afterSeparator = false
                    i++
                }
            }
        }
        if (chargeSign.isNotEmpty() || chargeDigits.isNotEmpty()) {
            // Magnitude before sign, matching chemistry convention: ³⁻, ²⁺.
            val magnitude = chargeDigits.mapNotNull { SUPERSCRIPT_DIGITS[it] }
                .joinToString(separator = "") { c -> c.toString() }
            val sign = when (chargeSign.firstOrNull()) {
                '+', SUPERSCRIPT_PLUS -> SUPERSCRIPT_PLUS.toString()
                else -> SUPERSCRIPT_MINUS.toString()
            }
            sb.append(magnitude + sign)
        }
        return sb.toString()
    }
}

/** Picks a deterministic substance from the offline database for a given day. */
object CompoundOfTheDayPicker {
    private val DAY_MS = 24 * 60 * 60 * 1000L

    fun pick(entries: List<ChemicalDbEntry>, dayIndex: Long): CompoundOfTheDay? {
        val substances = substancesOf(entries)
        if (substances.isEmpty()) return null
        val entry = substances[Math.floorMod(dayIndex.toInt(), substances.size)]
        return entry.toWidgetCompound()
    }

    fun dayIndex(nowMs: Long = System.currentTimeMillis()): Long = nowMs / DAY_MS

    internal fun substancesOf(entries: List<ChemicalDbEntry>): List<ChemicalDbEntry> =
        entries.filter { it.category == ChemicalDbCategory.SUBSTANCES && it.title.isNotBlank() }
}

/** Picks a fresh random substance, avoiding an immediate repeat when possible. */
object RandomCompoundPicker {
    fun pick(
        entries: List<ChemicalDbEntry>,
        previousIndex: Int?,
        random: Random = Random.Default
    ): Pair<Int, CompoundOfTheDay>? {
        val substances = CompoundOfTheDayPicker.substancesOf(entries)
        if (substances.isEmpty()) return null
        var index = random.nextInt(substances.size)
        var attempts = 0
        while (previousIndex != null && index == previousIndex && substances.size > 1 && attempts < 8) {
            index = random.nextInt(substances.size)
            attempts++
        }
        return index to substances[index].toWidgetCompound()
    }
}

/** Shared rendering + tap-to-search helpers for both widgets. */
object CompoundWidgetViews {
    fun applyCompound(views: RemoteViews, compound: CompoundOfTheDay?, nameId: Int, formulaId: Int, usesId: Int) {
        if (compound == null) {
            views.setTextViewText(nameId, "")
            views.setTextViewText(formulaId, "")
            views.setTextViewText(usesId, "")
            return
        }
        views.setTextViewText(nameId, compound.name)
        views.setTextViewText(formulaId, WidgetFormulas.format(compound.formula))
        views.setTextViewText(usesId, compound.uses)
    }

    fun tapToSearchIntent(context: Context, query: String?): Intent =
        Intent(context, com.furthersecrets.chemsearch.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(com.furthersecrets.chemsearch.ChemLaunchContract.EXTRA_TAB, com.furthersecrets.chemsearch.ChemLaunchContract.TAB_SEARCH)
            putExtra(com.furthersecrets.chemsearch.ChemLaunchContract.EXTRA_QUERY, query ?: "")
        }

    fun searchPendingIntent(context: Context, query: String?, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            tapToSearchIntent(context, query),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

/**
 * Compound-of-the-day home-screen widget. Content comes from the offline
 * chemical database and is derived purely from the calendar date, so every
 * device shows the same compound on the same day and it only changes at
 * midnight — unlike the random widget, there is no reroll button.
 */
class CompoundWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        scheduleDailyRefresh(context)
        render(context, appWidgetManager, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleDailyRefresh(context)
    }

    private fun render(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val compound = CompoundOfTheDayPicker.pick(
            ChemicalDatabase.load(context),
            CompoundOfTheDayPicker.dayIndex()
        )
        val views = RemoteViews(context.packageName, R.layout.compound_widget)
        CompoundWidgetViews.applyCompound(
            views, compound,
            R.id.widget_name, R.id.widget_formula, R.id.widget_uses
        )
        views.setOnClickPendingIntent(R.id.widget_root, CompoundWidgetViews.searchPendingIntent(context, compound?.name, 0))
        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "compound_widget_daily_refresh"

        /** Schedules the once-per-day widget refresh; safe to call repeatedly. */
        fun scheduleDailyRefresh(context: Context) {
            val request = PeriodicWorkRequestBuilder<CompoundWidgetRefreshWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelayToNextLocalMidnight(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        internal fun initialDelayToNextLocalMidnight(nowMs: Long = System.currentTimeMillis()): Long {
            val DAY_MS = 24 * 60 * 60 * 1000L
            val midnightToday = (nowMs / DAY_MS) * DAY_MS - timezoneOffsetMs(nowMs)
            val nextMidnight = midnightToday + DAY_MS
            return (nextMidnight - nowMs).coerceAtLeast(0L)
        }

        private fun timezoneOffsetMs(nowMs: Long): Long {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = nowMs
            return (cal.get(java.util.Calendar.ZONE_OFFSET) + cal.get(java.util.Calendar.DST_OFFSET)).toLong()
        }
    }
}

/** Recomputes the compound-of-the-day widget once per day in the background. */
class CompoundWidgetRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, CompoundWidgetProvider::class.java))
        if (ids.isEmpty()) return Result.success()
        val compound = CompoundOfTheDayPicker.pick(
            ChemicalDatabase.load(context),
            CompoundOfTheDayPicker.dayIndex()
        )
        val views = RemoteViews(context.packageName, R.layout.compound_widget)
        CompoundWidgetViews.applyCompound(
            views, compound,
            R.id.widget_name, R.id.widget_formula, R.id.widget_uses
        )
        views.setOnClickPendingIntent(R.id.widget_root, CompoundWidgetViews.searchPendingIntent(context, compound?.name, 0))
        manager.updateAppWidget(ids, views)
        return Result.success()
    }
}

/**
 * Random-compound widget. Shows a random substance from the offline chemical
 * database; the refresh button rerolls it, and tapping the card searches for
 * the shown compound. Entirely offline — no network, no settings.
 */
class RandomCompoundWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        render(context, appWidgetManager, appWidgetIds, randomize = false)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_RANDOMIZE) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, this::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids, randomize = true)
        }
    }

    private fun render(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray, randomize: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val entries = ChemicalDatabase.load(context)
        val substances = CompoundOfTheDayPicker.substancesOf(entries)
        val stored = prefs.getInt(KEY_LAST_INDEX, -1)
        val picked = when {
            // Refresh press: fresh random, avoiding an immediate repeat.
            randomize -> RandomCompoundPicker.pick(entries, stored.takeIf { it >= 0 })
            // Re-render (launcher update, reboot): keep the shown compound stable.
            stored in substances.indices -> stored to substances[stored].toWidgetCompound()
            // First ever render: pick a fresh random.
            else -> RandomCompoundPicker.pick(entries, null)
        }
        if (picked != null) {
            prefs.edit().putInt(KEY_LAST_INDEX, picked.first).apply()
        }
        val compound = picked?.second

        val views = RemoteViews(context.packageName, R.layout.random_compound_widget)
        CompoundWidgetViews.applyCompound(
            views, compound,
            R.id.widget_random_name, R.id.widget_random_formula, R.id.widget_random_uses
        )
        views.setTextViewText(R.id.widget_random_hint, context.getString(R.string.ui_widget_random_tap_refresh))
        views.setOnClickPendingIntent(R.id.widget_random_root, CompoundWidgetViews.searchPendingIntent(context, compound?.name, 0))

        val refreshIntent = Intent(context, RandomCompoundWidgetProvider::class.java).apply {
            action = ACTION_RANDOMIZE
        }
        val refreshPending = PendingIntent.getBroadcast(
            context,
            REFRESH_REQUEST_CODE,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_random_refresh, refreshPending)
        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }

    companion object {
        const val ACTION_RANDOMIZE = "com.furthersecrets.chemsearch.widget.RANDOMIZE_COMPOUND"
        const val PREFS_NAME = "chemsearch_widget_prefs"
        const val KEY_LAST_INDEX = "widget_random_last_index"
        const val REFRESH_REQUEST_CODE = 42
    }
}
