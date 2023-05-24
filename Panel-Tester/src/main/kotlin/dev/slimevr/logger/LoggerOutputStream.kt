package dev.slimevr.logger

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

class LoggerOutputStream(
    private val logger: Logger,
    private val level: Level,
    private val prefix: String) : ByteArrayOutputStream() {

    private val buffer = StringBuilder()

    constructor(logger: Logger, level: Level) : this(logger, level, "")

    @Throws(IOException::class)
    override fun flush() {
        synchronized(this) {
            super.flush()
            val record = this.toString()
            super.reset()
            if (record.isNotEmpty()) {
                buffer.append(record)
                if (record.contains(separator)) {
                    val s = buffer.toString()
                    val split = s.split(separator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    for (value in split) logger.log(level, prefix + value)
                    buffer.setLength(0)
                    // buffer.append(split[split.length - 1]);
                }
            }
        }
    }

    @Throws(IOException::class)
    override fun close() {
        flush()
    }

    companion object {
        private val separator = System.getProperty("line.separator")
    }
}
