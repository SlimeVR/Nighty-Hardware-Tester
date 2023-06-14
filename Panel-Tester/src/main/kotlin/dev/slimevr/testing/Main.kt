@file:JvmName("Main")

package dev.slimevr.testing

import com.pi4j.Pi4J
import com.pi4j.context.Context
import com.pi4j.io.i2c.I2CProvider
import dev.slimevr.database.RemoteTestingDatabase
import dev.slimevr.logger.LogManager
import dev.slimevr.testing.stage2.Stage2TestingSuite
import dev.slimevr.ui.TesterUI
import dev.slimevr.ui.stage2.Stage2UI
import java.io.File
import java.lang.Thread.sleep
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.system.exitProcess

var pi4j: Context? = null

fun main(args: Array<String>) {
    LogManager.initialize(File(""))
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        try {
            LogManager.global.log(Level.SEVERE, "Unhandled exception in ${t.name}", e)
        } catch (everythingDead: Throwable) {
            LogManager.onlyFileLogger.log(Level.SEVERE, "Error displaying unhanded exception", e)
            sleep(10000)
            exitProcess(-1)
        }
    }

    // Try adding program arguments via Run/Debug configuration.
    // Learn more about running applications: https://www.jetbrains.com/help/idea/running-applications.html.
    println("Program arguments: ${args.joinToString()}")

    val globalLogger = Logger.getLogger("")
    val statusLogger = Logger.getLogger("status")
    val devicesLogger = Logger.getLogger("devices")
    val database = RemoteTestingDatabase(
        System.getenv("TESTER_RPC_URL"),
        System.getenv("TESTER_RPC_PASSWORD"),
        "slime-tester-1",
        System.getenv("TESTER_REPORT_TYPE")
    )

    val stage = System.getenv("TESTER_STAGE")?.toInt()
    if (stage == 2) {
        val testerUI = Stage2UI(globalLogger, statusLogger, devicesLogger)
        sleep(500)
        val suite = Stage2TestingSuite(listOf(database), testerUI, globalLogger, statusLogger)
        suite.start()
    } else {
        val testerUi = TesterUI(globalLogger, statusLogger)
        sleep(500)

        pi4j = Pi4J.newAutoContext()
        val i2CProvider: I2CProvider = pi4j!!.provider("linuxfs-i2c")
        val switchboard = Switchboard(pi4j!!)
        val adcProvider = ADCProvider(i2CProvider)

        val suite =
            MainPanelTestingSuite(switchboard, adcProvider, listOf(database), testerUi, 10, globalLogger, statusLogger)
        suite.start()
        testerUi.registerTestingSuite(suite)
    }
}

fun destroy() {
    pi4j?.shutdown()
    sleep(200)
    exitProcess(0)
}
