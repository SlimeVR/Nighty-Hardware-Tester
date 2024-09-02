package dev.slimevr.ui.extensions

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.graphics.SimpleTheme
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.screen.Screen
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.terminal.Terminal
import dev.slimevr.logger.LogManager
import dev.slimevr.testing.TestStatus
import dev.slimevr.testing.destroy
import dev.slimevr.ui.*
import java.util.logging.Logger

class ExtensionsUpdaterUI (
    globalLogger: Logger,
    deviceLoggers: Array<Logger?>
) {
    val fullLogHandler: LabelLogHandler
    val testedDevicesUI = mutableListOf<TestingDeviceUI>()
    val window = BasicWindow()

    init {
        LogManager.removeNonFileHandlers()
        // Setup terminal and screen layers
        val terminal: Terminal = DefaultTerminalFactory().createTerminal()
        val screen: Screen = TerminalScreen(terminal)
        screen.startScreen()
        // Create window to hold the panel
        window.theme = SimpleTheme(TextColor.ANSI.WHITE, TextColor.ANSI.BLACK)
        window.setHints(listOf(Window.Hint.FULL_SCREEN, Window.Hint.FIT_TERMINAL_WINDOW))
        window.setCloseWindowWithEscape(true)

        // Create panel to hold components

        val mainPanel = Panel()
        mainPanel.setLayoutManager(GridLayout(deviceLoggers.size / 2))

        for (i in 1..deviceLoggers.size) {
            val testerPanel = Panel()
            val statusColorPanel = EmptySpace(TestStatus.DISCONNECTED.color,
                TerminalSize(1, 1))
            val idLabel = Label("Board $i")
            testerPanel.setLayoutManager(GridLayout(1))
            testerPanel.addComponent(statusColorPanel, GridLayout.createHorizontallyFilledLayoutData())
            testerPanel.addComponent(idLabel, GridLayout.createHorizontallyFilledLayoutData())
            val usbLabel = Label("")
            testerPanel.addComponent(usbLabel, GridLayout.createHorizontallyFilledLayoutData())

            val logLabel = SlimyLabel("")
            logLabel.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT)
            val deviceLogHandler = LabelLogHandler(logLabel, 20)
            deviceLogHandler.formatter = OnlyTextLogFormatter()
            deviceLoggers[i-1]?.addHandler(deviceLogHandler)
            deviceLoggers[i-1]?.useParentHandlers = false
            logLabel.setSize(TerminalSize(20, 20))

            testerPanel.addComponent(logLabel, GridLayout.createHorizontallyFilledLayoutData())

            mainPanel.addComponent(
                testerPanel.withBorder(Borders.singleLine("Board $i")),
                GridLayout.createLayoutData(GridLayout.Alignment.FILL,
                    GridLayout.Alignment.FILL, true,
                    true, 1, 1)
            )

            val deviceUI = TestingDeviceUI(i, testerPanel, statusColorPanel, idLabel, usbLabel, logLabel)
            testedDevicesUI.add(deviceUI)
        }
        val logLabel = SlimyLabel("")
        logLabel.setSize(TerminalSize(20, 20))
        logLabel.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT)
        fullLogHandler = LabelLogHandler(logLabel, 100)
        fullLogHandler.formatter = LabelLogFormatter()
        globalLogger.addHandler(fullLogHandler)
        mainPanel.addComponent(
            logLabel.withBorder(Borders.singleLine("Log")),
            GridLayout.createLayoutData(GridLayout.Alignment.FILL,
                GridLayout.Alignment.FILL, true,
                true, deviceLoggers.size / 2, 1)
        )

        window.component = mainPanel
        // Create gui and start gui
        Thread {
            val gui = MultiWindowTextGUI(screen, DefaultWindowManager(), EmptySpace(TextColor.ANSI.BLACK))
            gui.addWindowAndWait(window)
            destroy()
        }.start()
    }

    fun setStatus(device: Int, status: TestStatus) {
        testedDevicesUI[device].statusColorPanel.color = status.color
    }

    fun setID(device: Int, id: String) {
        testedDevicesUI[device].idLabel.text = id
    }

    fun clear(device: Int) {
        testedDevicesUI[device].idLabel.text = "Device ${device+1}"
        testedDevicesUI[device].statusColorPanel.color = TestStatus.DISCONNECTED.color
        testedDevicesUI[device].logLabel?.text = ""
        // todo : clear log
    }
}
