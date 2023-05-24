package dev.slimevr.ui

import java.io.PrintWriter
import java.io.StringWriter
import java.text.MessageFormat
import java.text.SimpleDateFormat
import java.util.logging.Formatter
import java.util.logging.LogRecord

class LabelLogFormatter : Formatter() {
    override fun format(record: LogRecord): String {
        val sb = StringBuilder()
        sb.append(dateFormat.format(record.millis))
        val localLevel = record.level
        sb.append(" [").append(localLevel.localizedName).append("] ")
        sb.append(record.message)
        sb.append('\n')
        val localThrowable = record.thrown
        if (localThrowable != null) {
            val localStringWriter = StringWriter()
            localThrowable.printStackTrace(PrintWriter(localStringWriter))
            sb.append(localStringWriter)
        }
        val message = sb.toString()
        val parameters = record.parameters
        if (parameters == null || parameters.isEmpty()) return message
        return if (message.contains("{0")
            || message.contains("{1")
            || message.contains("{2")
            || message.contains("{3")
        ) MessageFormat.format(message, *parameters) else message
    }

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    }
}
