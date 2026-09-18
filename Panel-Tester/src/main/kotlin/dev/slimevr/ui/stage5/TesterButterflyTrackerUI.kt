package dev.slimevr.ui.stage5

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
import dev.slimevr.testing.TestStatus
import dev.slimevr.testing.stage5.ButterflyTrackerPanelTestingSuite
import dev.slimevr.testing.destroy
import dev.slimevr.ui.LabelLogFormatter
import dev.slimevr.ui.LabelLogHandler
import dev.slimevr.ui.OnlyTextLogFormatter
import dev.slimevr.ui.SlimyLabel
import dev.slimevr.ui.TestingDeviceUI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

class TesterButterflyTrackerUI(
    globalLogger: Logger,
    statusLogger: Logger
) {

    val fullLogHandler: LabelLogHandler
    val statusLogHandler: LabelLogHandler
    val testedDevicesUI = mutableListOf<TestingDeviceUI>()
    val window = BasicWindow()
    val secondRowLabel = Label("Controlling: FIRST column")
    var secondColumn = false

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
        mainPanel.setLayoutManager(GridLayout(4))

        mainPanel.addComponent(
            secondRowLabel,
            GridLayout.createHorizontallyFilledLayoutData(4)
        )

        val placeholder = TestingDeviceUI(0, Panel(), EmptySpace(), Label(""), Label(""), null)
        for (i in 1..20)
            testedDevicesUI.add(placeholder)
        val panels = arrayOf(
            1, 6, 11, 16,
            2, 7, 12, 17,
            3, 8, 13, 18,
            4, 9, 14, 19,
            5, 10, 15, 20)
        for (i in panels) {
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

            testedDevicesUI[i - 1] = TestingDeviceUI(i, testerPanel, statusColorPanel, idLabel, usbLabel)
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
                false, 3, 1)
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

    fun registerTestingSuite(suite: ButterflyTrackerPanelTestingSuite) {
        window.addWindowListener(object: WindowListenerAdapter() {
            override fun onInput(basePane: Window?, keyStroke: KeyStroke?, deliverEvent: AtomicBoolean?) {
                if(!suite.isReady())
                    return
                val ch = keyStroke?.character
                val lch = ch?.lowercase()?.get(0)
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
                        if(secondColumn)
                            device += 10
                        suite.startTest(device)
                    }
                    lch == 'r' -> {
                        val failed = suite.getFailedDevices()
                        if(failed.isNotEmpty())
                            suite.startTest(*failed.toIntArray())
                    }
                    lch == 'u' -> {
                        suite.startTest(0, 1, 2, 3, 4)
                    }
                    lch == 'j' -> {
                        suite.startTest(5, 6, 7, 8, 9)
                    }
                    lch == '-' -> {
                        secondColumn = false
                        secondRowLabel.text = "Controlling: FIRST column"
                    }
                    lch == '+' -> {
                        secondColumn = true
                        secondRowLabel.text = "Controlling: SECOND column"
                    }
                    lch == 'g' -> {
                        suite.startTest(10, 11, 12, 13, 14)
                    }
                    lch == 'b' -> {
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

    fun clearAll() {
        for (deviceUI in testedDevicesUI) {
            deviceUI.idLabel.text = ""
            deviceUI.statusColorPanel.color = TestStatus.DISCONNECTED.color
        }
    }
}
