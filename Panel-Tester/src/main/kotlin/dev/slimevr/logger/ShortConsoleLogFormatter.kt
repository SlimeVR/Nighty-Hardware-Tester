package dev.slimevr.logger

import java.io.PrintWriter
import java.io.StringWriter
import java.text.MessageFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Formatter
import java.util.logging.LogRecord

class ShortConsoleLogFormatter : Formatter() {

    val date: SimpleDateFormat

    init {
        date = createDateFormat()
    }

    fun createDateFormat(): SimpleDateFormat {
        return SimpleDateFormat("HH:mm:ss")
    }

    fun buildMessage(builder: StringBuilder, record: LogRecord) {
        builder.append(date.format(record.millis))
        builder.append(" [")
        builder.append(record.level.localizedName.uppercase(Locale.getDefault()))
        builder.append("] ")
        builder.append(record.message)
        builder.append('\n')
    }

    override fun format(record: LogRecord): String {
        val builder = StringBuilder()
        val ex = record.thrown
        buildMessage(builder, record)
        if (ex != null) {
            val writer = StringWriter()
            ex.printStackTrace(PrintWriter(writer))
            builder.append(writer)
        }
        val message = builder.toString()
        val parameters = record.parameters
        if (parameters == null || parameters.size == 0) return message
        return if (message.contains("{0")
            || message.contains("{1")
            || message.contains("{2")
            || message.contains("{3")
        ) MessageFormat.format(message, *parameters) else message
    }
}
