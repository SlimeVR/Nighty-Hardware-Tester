package dev.slimevr.ui

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.graphics.SimpleTheme
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.screen.Screen
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.terminal.Terminal
import dev.slimevr.logger.LogManager
import dev.slimevr.testing.stage1.MainPanelTestingSuite
import dev.slimevr.testing.TestStatus
import dev.slimevr.testing.destroy
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

class TesterUI(
    globalLogger: Logger,
    statusLogger: Logger
) {

    val fullLogHandler: LabelLogHandler
    val statusLogHandler: LabelLogHandler
    val testedDevicesUI = mutableListOf<TestingDeviceUI>()
    val window = BasicWindow()
    val secondRowLabel = Label("Controlling: FIRST row")
    var secondRow = false

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
        mainPanel.setLayoutManager(GridLayout(5))

        mainPanel.addComponent(
            secondRowLabel,
            GridLayout.createHorizontallyFilledLayoutData(5)
        )

        for (i in 1..20) {
            val testerPanel = Panel()
            val statusColorPanel = EmptySpace(TestStatus.DISCONNECTED.color,
                TerminalSize(1, 1))
            val idLabel = Label("Board $i")
            testerPanel.setLayoutManager(GridLayout(1))
            testerPanel.addComponent(statusColorPanel, GridLayout.createHorizontallyFilledLayoutData())
            testerPanel.addComponent(idLabel, GridLayout.createHorizontallyFilledLayoutData())
            val usbLabel = Label("")
            testerPanel.addComponent(usbLabel, GridLayout.createHorizontallyFilledLayoutData())
            mainPanel.addComponent(
                testerPanel.withBorder(Borders.singleLine("Board $i")),
                GridLayout.createHorizontallyFilledLayoutData()
            )

            val deviceUI = TestingDeviceUI(i, testerPanel, statusColorPanel, idLabel, usbLabel)
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

    fun registerTestingSuite(suite: MainPanelTestingSuite) {
        window.addWindowListener(object: WindowListenerAdapter() {
            override fun onInput(basePane: Window?, keyStroke: KeyStroke?, deliverEvent: AtomicBoolean?) {
                if(!suite.isReady())
                    return
                val ch = keyStroke?.character
                when {
                    ch == null -> {}
                    ch == ' ' -> {
                        suite.btnPressed()
                        suite.startTest()
                    }
                    ch.isDigit() -> {
                        var device = keyStroke.character.digitToInt()
                        if(device == 0)
                            device = 9
                        else
                            device -= 1
                        if(secondRow)
                            device += 10
                        suite.startTest(device)
                    }
                    ch == 'r' -> {
                        val failed = suite.getFailedDevices()
                        if(failed.isNotEmpty())
                            suite.startTest(*failed.toIntArray())
                    }
                    ch == 'u' -> {
                        suite.startTest(0, 1, 2, 3, 4)
                    }
                    ch == 'j' -> {
                        suite.startTest(5, 6, 7, 8, 9)
                    }
                    ch == 't' -> {
                        suite.transposeDevices()
                        val failed = suite.getFailedDevices()
                        if(failed.isNotEmpty()) {
                            suite.startTest(*failed.toIntArray())
                        }
                    }
                    ch == '-' -> {
                        secondRow = false
                        secondRowLabel.text = "Controlling: FIRST row"
                    }
                    ch == '+' -> {
                        secondRow = true
                        secondRowLabel.text = "Controlling: SECOND row"
                    }
                    ch == 'g' -> {
                        suite.startTest(10, 11, 12, 13, 14)
                    }
                    ch == 'b' -> {
                        suite.startTest(15, 16, 17, 18, 19)
                    }
                }
            }
        })
    }

    fun setStatus(device: Int, status: TestStatus) {
        testedDevicesUI[device].statusColorPanel.color = status.color
    }

    fun setID(device: Int, id: String) {
        testedDevicesUI[device].idLabel.text = id
    }

    fun setUSB(device: Int, usb: String) {
        testedDevicesUI[device].usbLabel.text = usb
    }

    fun clear() {
        for (deviceUI in testedDevicesUI) {
            deviceUI.idLabel.text = ""
            deviceUI.statusColorPanel.color = TestStatus.DISCONNECTED.color
        }
    }


}
