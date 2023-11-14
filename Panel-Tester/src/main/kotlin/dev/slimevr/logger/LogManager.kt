package dev.slimevr.logger

import java.io.File
import java.io.IOException
import java.io.PrintStream
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.*

object LogManager {
    private val initialized = AtomicBoolean(false)
    var global = Logger.getLogger("")
    var onlyFileLogger = Logger.getLogger("file-logger")

    @Throws(SecurityException::class, IOException::class)
    fun initialize(mainLogDir: File) {
        if (initialized.getAndSet(true))
            return
        val loc = FileLogFormatter()
        // Ensure the log directory exists
        if (!mainLogDir.exists()) mainLogDir.mkdirs()

        // Clean old log files if they exist
        val logFiles = mainLogDir.listFiles()
        if (logFiles != null) {
            for (f in logFiles) {
                if (f.name.startsWith("log_last")) f.delete()
            }
        }
        val lastLogPattern = Path.of(mainLogDir.path, "log_last.log").toString()
        val fileHandler = FileHandler(lastLogPattern, 25 * 1000000, 1)
        fileHandler.formatter = loc
        global.addHandler(fileHandler)

        val onlyFileLoggerHandler = FileHandler("unhandled.log", 25 * 1000000, 1)
        onlyFileLoggerHandler.formatter = loc
        onlyFileLogger.addHandler(onlyFileLoggerHandler)
        onlyFileLogger.useParentHandlers = false
    }

    fun removeNonFileHandlers() {
        for (handler in global.handlers) {
            if(handler !is FileHandler) {
                handler.close()
                global.removeHandler(handler)
            }
        }
    }

    init {
        //System.setOut(PrintStream(LoggerOutputStream(global, Level.INFO), true))
        //System.setErr(PrintStream(LoggerOutputStream(global, Level.SEVERE), true))
    }
}
