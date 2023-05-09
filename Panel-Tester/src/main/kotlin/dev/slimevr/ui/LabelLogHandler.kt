package dev.slimevr.ui

import com.googlecode.lanterna.gui2.Label
import java.util.ArrayList
import java.util.logging.Handler
import java.util.logging.LogRecord

class LabelLogHandler(
    val label: Label,
    val maxLines: Int
): Handler() {

    val allLines = ArrayList<String>(maxLines)

    override fun publish(record: LogRecord?) {
        if (record != null) {
            var lines = record.message.split("\n").toMutableList()
            lines[0] = record.level.name + ": " + lines[0]
            addLines(lines)
        }
    }

    private fun addLines(lines: List<String>) {
        while(allLines.size + lines.size > maxLines) {
            allLines.removeAt(0)
        }
        allLines.addAll(lines)
        var fittingLines = label.size.rows
        var actualLines = fittingLines.coerceAtMost(allLines.size)
        var start = allLines.size - actualLines
        label.text = allLines.subList(start, start + actualLines).joinToString("\n")
    }

    override fun flush() {
    }

    override fun close() {
    }
}
