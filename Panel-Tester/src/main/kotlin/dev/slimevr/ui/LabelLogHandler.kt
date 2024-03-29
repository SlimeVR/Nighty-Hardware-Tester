package dev.slimevr.ui

import com.googlecode.lanterna.gui2.Label
import java.util.ArrayList
import java.util.logging.Handler
import java.util.logging.LogRecord

class LabelLogHandler(
    private val label: SlimyLabel,
    private val maxLines: Int
): Handler() {

    private val allLines = ArrayList<String>(maxLines)

    override fun publish(record: LogRecord?) {
        if (record != null) {
            //label.labelWidth = label.size.columns
            var lines = formatter.format(record).split("\n").toMutableList().filter { it.isNotBlank() }
            addLines(lines)
        }
    }

    @Synchronized
    private fun addLines(lines: List<String>) {
        allLines.addAll(lines)
        while(allLines.size > maxLines) {
            allLines.removeAt(0)
        }
        updatePanel()
    }

    @Synchronized
    fun clear() {
        allLines.clear()
        updatePanel()
    }

    private fun updatePanel() {
        //var fittingLines = label.size.rows
        //var actualLines = fittingLines.coerceAtMost(allLines.size)
        //var start = allLines.size - actualLines
        //label.text = allLines.subList(start, start + actualLines).joinToString("\n")
        label.text = allLines.joinToString("\n")
    }

    override fun flush() {
    }

    override fun close() {
        clear()
    }
}
