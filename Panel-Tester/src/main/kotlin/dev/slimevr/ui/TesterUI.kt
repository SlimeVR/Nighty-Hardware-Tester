package dev.slimevr.ui

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.graphics.SimpleTheme
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.screen.Screen
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.terminal.Terminal
import dev.slimevr.testing.TestStatus
import java.util.*
import java.util.logging.Logger
import kotlin.system.exitProcess

class TesterUI {

    val fullLogHandler: LabelLogHandler
    val statusLogNandler: LabelLogHandler
    val testedDevicesUI = mutableListOf<TestingDeviceUI>()

    init {
        // Setup terminal and screen layers
        val terminal: Terminal = DefaultTerminalFactory().createTerminal()
        val screen: Screen = TerminalScreen(terminal)
        screen.startScreen()
        // Create window to hold the panel
        val window = BasicWindow()
        window.theme = SimpleTheme(TextColor.ANSI.WHITE, TextColor.ANSI.BLACK)
        window.setHints(listOf(Window.Hint.FULL_SCREEN))

        // Create panel to hold components

        val mainPanel = Panel()
        mainPanel.setLayoutManager(GridLayout(5))

        for(i in 1..10) {
            val testerPanel = Panel()
            val statusColorPanel = EmptySpace(TestStatus.DISCONNECTED.color, TerminalSize(16,1))
            val idLabel = Label(UUID.randomUUID().toString())
            testerPanel.setLayoutManager(GridLayout(1))
            testerPanel.addComponent(statusColorPanel, GridLayout.createHorizontallyFilledLayoutData())
            testerPanel.addComponent(idLabel, GridLayout.createHorizontallyFilledLayoutData())
            mainPanel.addComponent(testerPanel.withBorder(Borders.singleLine("Board $i")), GridLayout.createHorizontallyFilledLayoutData())

            val deviceUI = TestingDeviceUI(i, testerPanel, statusColorPanel, idLabel)
            testedDevicesUI.add(deviceUI)
        }
        val logLabel = Label("")
        logLabel.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT)
        fullLogHandler = LabelLogHandler(logLabel, 100)
        fullLogHandler.formatter = LogFormatter()
        Logger.getLogger("").addHandler(fullLogHandler)
        mainPanel.addComponent(logLabel.withBorder(Borders.singleLine("Log")), GridLayout.createLayoutData(GridLayout.Alignment.FILL, GridLayout.Alignment.FILL, true,  true, 3, 1))

        val statusLabel = Label("")
        statusLabel.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT)
        statusLogNandler = LabelLogHandler(statusLabel, 100)
        statusLogNandler.formatter = OnlyTextLogFormatter()
        val statusLogger = Logger.getLogger("Status logger")
        statusLogger.addHandler(statusLogNandler)
        statusLogger.useParentHandlers = false
        mainPanel.addComponent(statusLabel, GridLayout.createLayoutData(GridLayout.Alignment.FILL, GridLayout.Alignment.FILL, true,  true, 2, 1))

        window.component = mainPanel
        // Create gui and start gui
        Thread {
            val gui = MultiWindowTextGUI(screen, DefaultWindowManager(), EmptySpace(TextColor.ANSI.BLACK))
            gui.addWindowAndWait(window)
            exitProcess(0)
        }.start()
    }

    fun setStatus(device: Int, status: TestStatus) {
        testedDevicesUI[device].statusColorPanel.color = status.color
    }

    fun setID(device: Int, id: String) {
        testedDevicesUI[device].idLabel.text = id
    }

    fun clear() {
        for(deviceUI in testedDevicesUI) {
            deviceUI.idLabel.text = ""
            deviceUI.statusColorPanel.color = TestStatus.DISCONNECTED.color
        }
    }
}
