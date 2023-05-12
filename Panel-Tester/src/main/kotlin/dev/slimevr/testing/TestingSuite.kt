package dev.slimevr.testing

import dev.slimevr.database.TestingDatabase
import dev.slimevr.ui.TesterUI
import java.util.logging.Logger

class TestingSuite(
    val switchboard: Switchboard,
    val adcProvider: ADCProvider,
    val testingDatabases: List<TestingDatabase>,
    val testerUi: TesterUI
): Thread("Testing suit thread") {

    private val logger: Logger = Logger.getLogger("Testing Suit")
    private var statusLogger: Logger = Logger.getLogger("Status logger")

    override fun run() {
        logger.info("Testing suit started~")
        while(true) {
            testerUi.statusLogNandler.clear()
            statusLogger.info("Ready to start the test")
            while(!switchboard.isButtonPressed()) {
                sleep(10)
            }
            statusLogger.info("We goin'~")
        }
    }
}
