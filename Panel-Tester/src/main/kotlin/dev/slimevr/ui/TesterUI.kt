package dev.slimevr.ui

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
import java.util.logging.Logger
import kotlin.system.exitProcess

class TesterUI(
    globalLogger: Logger,
    statusLogger: Logger
) {

    val fullLogHandler: LabelLogHandler
    val statusLogHandler: LabelLogHandler
    val testedDevicesUI = mutableListOf<TestingDeviceUI>()

    init {
        LogManager.removeNonFileHandlers()
        // Setup terminal and screen layers
        val terminal: Terminal = DefaultTerminalFactory().createTerminal()
        val screen: Screen = TerminalScreen(terminal)
        screen.startScreen()
        // Create window to hold the panel
        val window = BasicWindow()
        window.theme = SimpleTheme(TextColor.ANSI.WHITE, TextColor.ANSI.BLACK)
        window.setHints(listOf(Window.Hint.FULL_SCREEN, Window.Hint.FIT_TERMINAL_WINDOW))
        window.setCloseWindowWithEscape(true)

        // Create panel to hold components

        val mainPanel = Panel()
        mainPanel.setLayoutManager(GridLayout(5))

        for (i in 1..10) {
            val testerPanel = Panel()
            val statusColorPanel = EmptySpace(TestStatus.DISCONNECTED.color,
                TerminalSize(1, 1))
            val idLabel = Label("Board $i")
            testerPanel.setLayoutManager(GridLayout(1))
            testerPanel.addComponent(statusColorPanel, GridLayout.createHorizontallyFilledLayoutData())
            testerPanel.addComponent(idLabel, GridLayout.createHorizontallyFilledLayoutData())
            mainPanel.addComponent(
                testerPanel.withBorder(Borders.singleLine("Board $i")),
                GridLayout.createHorizontallyFilledLayoutData()
            )

            val deviceUI = TestingDeviceUI(i, testerPanel, statusColorPanel, idLabel)
            testedDevicesUI.add(deviceUI)
        }
        val logLabel = SlimyLabel("")
        logLabel.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT)
        fullLogHandler = LabelLogHandler(logLabel, 100)
        fullLogHandler.formatter = LabelLogFormatter()
        globalLogger.addHandler(fullLogHandler)
        mainPanel.addComponent(
            logLabel.withBorder(Borders.singleLine("Log")),
            GridLayout.createLayoutData(GridLayout.Alignment.FILL,
                GridLayout.Alignment.FILL, false,
                false, 4, 1)
        )

        val statusLabel = SlimyLabel("")
        statusLabel.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT)
        statusLogHandler = LabelLogHandler(statusLabel, 100)
        statusLogHandler.formatter = OnlyTextLogFormatter()
        statusLogger.addHandler(statusLogHandler)
        //statusLogger.useParentHandlers = false
        mainPanel.addComponent(
            statusLabel.withBorder(Borders.singleLine("Status")),
            GridLayout.createLayoutData(GridLayout.Alignment.FILL,
                GridLayout.Alignment.FILL, false,
                true, 1, 1)
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

    fun clear() {
        for (deviceUI in testedDevicesUI) {
            deviceUI.idLabel.text = ""
            deviceUI.statusColorPanel.color = TestStatus.DISCONNECTED.color
        }
    }


}
