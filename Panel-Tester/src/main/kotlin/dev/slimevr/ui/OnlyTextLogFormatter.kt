package dev.slimevr.ui

import java.io.PrintWriter
import java.io.StringWriter
import java.text.MessageFormat
import java.text.SimpleDateFormat
import java.util.logging.Formatter
import java.util.logging.LogRecord

class OnlyTextLogFormatter : Formatter() {
    override fun format(record: LogRecord): String {
        val sb = StringBuilder()
        sb.append(record.message)
        val localThrowable = record.thrown
        if (localThrowable != null) {
            val localStringWriter = StringWriter()
            localThrowable.printStackTrace(PrintWriter(localStringWriter))
            sb.append(localStringWriter)
        }
        val message = sb.toString().replace("\t", "  ")
        val parameters = record.parameters
        if (parameters == null || parameters.isEmpty()) return message
        return if (message.contains("{0")
            || message.contains("{1")
            || message.contains("{2")
            || message.contains("{3")
        ) MessageFormat.format(message, *parameters) else message
    }
}
