package dev.slimevr.database

import dev.slimevr.logger.LogManager
import java.io.FileReader
import java.io.IOException
import java.io.FileWriter
import java.util.logging.Level
import java.util.logging.Logger

class TestedDevicesLocalCache(
    private val filepath: String,
    private val logger: Logger,
    private var statusLogger: Logger
) {

    private val committedSuccessfulDeviceIds = mutableListOf<String>()

    fun load() {
        try {
            val fw = FileReader(filepath)
            fw.forEachLine {
                committedSuccessfulDeviceIds.add(it)
                if (committedSuccessfulDeviceIds.size > 30)
                    committedSuccessfulDeviceIds.removeAt(0)
            }
            logger.info("Loaded ${committedSuccessfulDeviceIds.size} old boards")
        } catch (ex: IOException) {
            logger.info("Commited device list read error: (${ex.message})")
            LogManager.exceptionsLogger.log(Level.SEVERE, "Commited device list read error: (${ex.message})", ex)
        }
    }

    fun has(deviceId: String) : Boolean = committedSuccessfulDeviceIds.contains(deviceId)

    fun append(deviceId: String) {
        committedSuccessfulDeviceIds.add(deviceId)
        if (committedSuccessfulDeviceIds.size > 30)
            committedSuccessfulDeviceIds.removeAt(0)
        try {
            val fw = FileWriter(filepath, true)
            fw.append(deviceId).append('\n')
            fw.close()
        } catch (ex: IOException) {
            statusLogger.info("Commited device list write error: ${ex.message}")
            LogManager.exceptionsLogger.log(Level.SEVERE, "Commited device list write error: (${ex.message})", ex)
        }
    }
}
