package dev.slimevr.testing

import com.pi4j.Pi4J
import com.pi4j.io.i2c.I2CProvider
import dev.slimevr.database.RemoteTestingDatabase
import dev.slimevr.ui.TesterUI


fun main(args: Array<String>) {
    // Try adding program arguments via Run/Debug configuration.
    // Learn more about running applications: https://www.jetbrains.com/help/idea/running-applications.html.
    println("Program arguments: ${args.joinToString()}")

    val testerUi = TesterUI()

    val pi4j = Pi4J.newAutoContext()
    val i2CProvider: I2CProvider = pi4j.provider("linuxfs-i2c")

    val switchboard = Switchboard(pi4j)
    var adcProvider = ADCProvider(i2CProvider)
    var database = RemoteTestingDatabase()

    TestingSuite(switchboard, adcProvider, listOf(database), testerUi).start()
}
