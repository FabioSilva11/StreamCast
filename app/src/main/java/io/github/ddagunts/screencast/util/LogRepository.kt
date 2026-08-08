package io.github.ddagunts.screencast.util

import android.util.Log
import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object LogRepository {
    enum class Level { D, I, W, E }
    data class Entry(val ts: Long, val level: Level, val tag: String, val msg: String)

    private val _flow = MutableSharedFlow<Entry>(replay = 500, extraBufferCapacity = 500,
        onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val flow = _flow.asSharedFlow()

    private var logFile: File? = null
    private val fileLock = Any()
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun initialize(context: Context) {
        synchronized(fileLock) {
            if (logFile != null) return
            logFile = File(context.applicationContext.filesDir, "streamcast-log.txt")
            runCatching {
                if (logFile!!.length() == 0L) logFile!!.appendText("StreamCast log started\n")
            }
        }
    }

    fun filePath(context: Context): File = logFile ?: File(context.applicationContext.filesDir, "streamcast-log.txt")

    fun clear(context: Context) {
        synchronized(fileLock) {
            filePath(context).writeText("StreamCast log cleared\n")
        }
    }

    fun log(level: Level, tag: String, msg: String) {
        when (level) {
            Level.D -> Log.d(tag, msg)
            Level.I -> Log.i(tag, msg)
            Level.W -> Log.w(tag, msg)
            Level.E -> Log.e(tag, msg)
        }
        val now = System.currentTimeMillis()
        _flow.tryEmit(Entry(now, level, tag, msg))
        synchronized(fileLock) {
            runCatching {
                val file = logFile ?: return@runCatching
                file.appendText("${timeFormat.format(Date(now))} [${level.name}] $tag: $msg\n")
            }
        }
    }
}

fun Any.logD(msg: String) = LogRepository.log(LogRepository.Level.D, javaClass.simpleName, redact(msg))
fun Any.logI(msg: String) = LogRepository.log(LogRepository.Level.I, javaClass.simpleName, redact(msg))
fun Any.logW(msg: String) = LogRepository.log(LogRepository.Level.W, javaClass.simpleName, redact(msg))
fun Any.logE(msg: String, t: Throwable? = null) =
    LogRepository.log(LogRepository.Level.E, javaClass.simpleName,
        redact(if (t == null) msg else "$msg: ${t.javaClass.simpleName}: ${t.message}"))

// Session/transport IDs and the per-session HTTP token are enough to hijack the
// cast while it's live — a user who shares logs for debugging shouldn't leak them.
private val SECRETS = listOf(
    Regex("""("(?:sessionId|transportId)"\s*:\s*")([^"]+)(")""") to """$1***$3""",
    Regex("""(/c/)([A-Za-z0-9_\-]+)(/)""") to """$1***$3""",
)

private fun redact(msg: String): String {
    var out = msg
    for ((re, repl) in SECRETS) out = re.replace(out, repl)
    return out
}
