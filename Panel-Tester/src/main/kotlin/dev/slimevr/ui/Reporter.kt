package dev.slimevr.ui

import java.util.logging.Level
import java.util.logging.Logger

class Reporter(
    number: Int,
    val logger: Logger
) {
    private val consoleLogger: Logger = Logger.getLogger("Reporter $number")

    fun log(level: Level, msg: String, params: Array<Object>) {
        consoleLogger.log(level, msg, params)
        logger.log(level, msg, params)
    }

    fun log(level: Level, msg: String) {
        consoleLogger.log(level, msg)
        logger.log(level, msg)
    }

    fun log(level: Level, msg: String, param: Object) = log(level, msg, arrayOf(param))

    fun warning(msg: String, params: Array<Object>) = log(Level.WARNING, msg, params)

    fun warning(msg: String) = log(Level.WARNING, msg)

    fun info(msg: String, params: Array<Object>) = log(Level.INFO, msg, params)

    fun info(msg: String) = log(Level.INFO, msg)

}

