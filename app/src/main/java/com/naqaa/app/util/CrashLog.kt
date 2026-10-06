package com.naqaa.app.util

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Keeps the last failure on the device so the user can read it out.
 *
 * A crash cannot be diagnosed from a screenshot of a dialog, and this application is not
 * allowed to upload anything. So the trace of an uncaught exception is written to a private
 * file, and a marker records that a launch started before the interface composed. The next
 * start offers the text with a copy button, and deletes it once the user has seen it.
 *
 * The text holds the version, the Android level, the processor list and the stack trace.
 * Nothing about the journal, the reasons or the contact is included: those live in other
 * files and are never touched here.
 */
object CrashLog {

    enum class Kind { EXCEPTION, INTERRUPTED }

    data class Report(val kind: Kind, val text: String)

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(appContext, EXCEPTION_FILE, describe(appContext, thread.name, error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Records a failure that a component caught itself. A service that keeps failing is
     * restarted by the system a few times and then switched off, which looks like an
     * application that opens and closes; the trace has to survive that.
     */
    fun note(context: Context, where: String, error: Throwable) {
        val appContext = context.applicationContext
        runCatching { write(appContext, EXCEPTION_FILE, describe(appContext, where, error)) }
    }

    /** Written before anything else starts, so a killed launch leaves a trace behind. */
    fun sessionStarted(context: Context) {
        val appContext = context.applicationContext
        runCatching { write(appContext, SESSION_FILE, summary(appContext)) }
    }

    /** Called once the interface composed: the launch reached the end of the risky part. */
    fun sessionComposed(context: Context) {
        runCatching { File(context.applicationContext.filesDir, SESSION_FILE).delete() }
    }

    fun pending(context: Context): Report? {
        val appContext = context.applicationContext
        File(appContext.filesDir, EXCEPTION_FILE).takeIf(File::exists)?.let {
            return Report(Kind.EXCEPTION, read(it))
        }
        File(appContext.filesDir, SESSION_FILE).takeIf(File::exists)?.let {
            return Report(Kind.INTERRUPTED, read(it))
        }
        return null
    }

    fun clear(context: Context) {
        val appContext = context.applicationContext
        runCatching { File(appContext.filesDir, EXCEPTION_FILE).delete() }
        runCatching { File(appContext.filesDir, SESSION_FILE).delete() }
    }

    private fun read(file: File): String = runCatching { file.readText() }.getOrDefault("")

    private fun write(context: Context, name: String, text: String) {
        File(context.filesDir, name).writeText(text)
    }

    private fun describe(context: Context, thread: String, error: Throwable): String =
        summary(context) + "\nthread: $thread\n" + error.stackTraceToString()

    private fun summary(context: Context): String {
        val version = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${info.versionCode})"
        }.getOrDefault("unknown")
        return buildString {
            appendLine("version: $version")
            appendLine("android: ${Build.VERSION.RELEASE} (api ${Build.VERSION.SDK_INT})")
            appendLine("processors: ${Build.SUPPORTED_ABIS.joinToString()}")
        }
    }

    private const val EXCEPTION_FILE = "last-error.txt"
    private const val SESSION_FILE = "last-launch.txt"
}
