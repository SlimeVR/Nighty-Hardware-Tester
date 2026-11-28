package dev.slimevr.ui.stage6

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
import dev.slimevr.testing.destroy
import dev.slimevr.testing.stage6.ButterflyDonglePanelTestingSuite
import dev.slimevr.ui.*
import java.util.logging.Logger
import java.util.concurrent.atomic.AtomicBoolean

class TesterButterflyDongleUI (
    globalLogger: Logger,
    val deviceLoggers: Array<Logger>
) {
    val fullLogHandler: LabelLogHandler
    val deviceLogHandlers: Array<LabelLogHandler>
    val testedDevicesUI: Array<TestingDeviceUI>
    val window = BasicWindow()

    val firstRowText = "Controlling: LEFT side"
    val secondRowText = "Controlling: RIGHT side"
    val secondRowLabel: Label
    var secondRow = false

    init {
        LogManager.removeNonFileHandlers()
        // Setup terminal and screen layers
        val terminal: Terminal = DefaultTerminalFactory().createTerminal()
        val screen: Screen = TerminalScreen(terminal)

        screen.startScreen()
        // Create window to hold the panel
        window.theme = SimpleTheme(TextColor.ANSI.WHITE_BRIGHT, TextColor.ANSI.BLACK)
        window.setHints(listOf(Window.Hint.FULL_SCREEN, Window.Hint.FIT_TERMINAL_WINDOW, Window.Hint.NO_DECORATIONS))
        window.setCloseWindowWithEscape(true)

        // Create panel to hold components

        val gridSize = deviceLoggers.size / 2
        val mainPanel = Panel()
        mainPanel.setLayoutManager(GridLayout(gridSize)
            .apply{
                horizontalSpacing = 0
                leftMarginSize = 0
                rightMarginSize = 0
            })

        secondRowLabel = Label(firstRowText).setSize(TerminalSize.ONE)
        mainPanel.addComponent(
            secondRowLabel,
            GridLayout.createHorizontallyFilledLayoutData(gridSize)
        )

        val devices = arrayOfNulls<TestingDeviceUI>(20)
        val handlers = arrayOfNulls<LabelLogHandler>(20)
        val panel = arrayOf(
            1, 3, 5, 7, 9, 11, 13, 15, 17, 19,
            2, 4, 6, 8, 10, 12, 14, 16, 18, 20
        )
        for (i in panel) {
            val testerPanel = Panel()
            val statusColorPanel = EmptySpace(TestStatus.DISCONNECTED.color,
                TerminalSize.ONE)
            val idLabel = Label("Board $i").setSize(TerminalSize.ONE)
            testerPanel.setLayoutManager(GridLayout(1)
                .apply{
                    leftMarginSize = 0
                    rightMarginSize = 0
                })
            testerPanel.addComponent(statusColorPanel, GridLayout.createHorizontallyFilledLayoutData())
            testerPanel.addComponent(idLabel, GridLayout.createHorizontallyEndAlignedLayoutData(1))
            val usbLabel = Label("").setSize(TerminalSize.ONE)
            testerPanel.addComponent(usbLabel, GridLayout.createHorizontallyEndAlignedLayoutData(1))

            val logLabel = SlimyLabel("")
            val deviceLogHandler = LabelLogHandler(logLabel, 20)
            deviceLogHandler.formatter = OnlyTextLogFormatter()
            deviceLoggers[i - 1].addHandler(deviceLogHandler)
            //deviceLoggers[i - 1].useParentHandlers = false
            logLabel.setSize(TerminalSize(20, 21))

            testerPanel.addComponent(logLabel,
                GridLayout.createLayoutData(GridLayout.Alignment.FILL, GridLayout.Alignment.CENTER, true, true, 1, 1)
            )

            mainPanel.addComponent(
                testerPanel.withBorder(Borders.singleLine("Board $i")),
                GridLayout.createHorizontallyFilledLayoutData()
            )

            handlers[i - 1] = deviceLogHandler
            devices[i - 1] = TestingDeviceUI(i, testerPanel, statusColorPanel, idLabel, usbLabel, logLabel)
        }
        testedDevicesUI = devices.map { it!! }.toTypedArray()
        deviceLogHandlers = handlers.map { it!! }.toTypedArray()

        val logLabel = SlimyLabel("")
        logLabel.setSize(TerminalSize(20, 12))
        fullLogHandler = LabelLogHandler(logLabel, 100)
        fullLogHandler.formatter = LabelLogFormatter()
        globalLogger.addHandler(fullLogHandler)
        mainPanel.addComponent(
            logLabel.withBorder(Borders.singleLine("Log")),
            GridLayout.createHorizontallyFilledLayoutData(gridSize)
        )

        window.component = mainPanel
        // Create gui and start gui
        Thread {
            val gui = MultiWindowTextGUI(screen, DefaultWindowManager(), EmptySpace(TextColor.ANSI.BLACK))
            //val gui = MultiWindowTextGUI(SeparateTextGUIThread.Factory(), screen)
            //(gui.getGUIThread() as AsynchronousTextGUIThread).start()
            gui.addWindowAndWait(window)
            destroy()
        }.start()
    }

    fun registerTestingSuite(suite: ButterflyDonglePanelTestingSuite) {
        window.addWindowListener(object: WindowListenerAdapter() {
            override fun onInput(basePane: Window?, keyStroke: KeyStroke?, deliverEvent: AtomicBoolean?) {
                if(!suite.isReady())
                    return
                val ch = keyStroke?.character
                val lch = ch?.lowercase()?.get(0)
                when {
                    ch == null -> {}
                    ch == ' ' -> {
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
                    lch == 'r' -> {
                        val failed = suite.getFailedDevices()
                        if(failed.isNotEmpty())
                            suite.startTest(*failed.toIntArray(), reflash = false)
                    }
                    lch == '-' -> {
                        secondRow = false
                        secondRowLabel.text = firstRowText
                    }
                    lch == '+' || lch == '=' -> {
                        secondRow = true
                        secondRowLabel.text = secondRowText
                    }
                    lch == 'e' -> {
                        suite.startTest(reflash = true, eraseFlash = true)
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

    fun clear(device: Int) {
        testedDevicesUI[device].idLabel.text = ""
        testedDevicesUI[device].usbLabel.text = ""
        testedDevicesUI[device].statusColorPanel.color = TestStatus.DISCONNECTED.color
        testedDevicesUI[device].logLabel?.text = ""
        deviceLogHandlers[device].clear()

    }

    fun clearAll() {
        for (i in deviceLoggers.indices)
            clear(i)
    }
}
