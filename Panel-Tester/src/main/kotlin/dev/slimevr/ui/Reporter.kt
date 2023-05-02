package dev.slimevr.ui

import java.util.logging.Level
import java.util.logging.Logger

class Reporter(
    number: Int
) {
    private val consoleLogger: Logger = Logger.getLogger("Reporter $number")

    fun log(level: Level, msg: String, params: Array<Object>) {
        //TODO display in window
        consoleLogger.log(level, msg, params)
    }

    fun log(level: Level, msg: String) {
        //TODO display in console
        consoleLogger.log(level, msg)
    }

    fun log(level: Level, msg: String, param: Object) = log(level, msg, arrayOf(param))

    fun warning(msg: String, params: Array<Object>) = log(Level.WARNING, msg, params)

    fun warning(msg: String) = log(Level.WARNING, msg)

    fun info(msg: String, params: Array<Object>) = log(Level.INFO, msg, params)

    fun info(msg: String) = log(Level.INFO, msg)

}

