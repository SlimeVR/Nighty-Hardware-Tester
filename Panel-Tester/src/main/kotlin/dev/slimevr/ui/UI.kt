package dev.slimevr.ui

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.graphics.SimpleTheme
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.screen.Screen
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.terminal.Terminal
import java.util.logging.Logger

class UI {

    var testerPanels = arrayOfNulls<Panel>(10)
    var logLabel = Label("Log")

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

        for(i in 0..9) {
            val testerPanel = Panel()
            testerPanels[i] = testerPanel
            testerPanel.setLayoutManager(GridLayout(2))
            testerPanel.preferredSize = TerminalSize(16, 20)
            testerPanel.addComponent(Label("~~~~"))
            testerPanel.addComponent(EmptySpace(TerminalSize(0,0)))
            testerPanel.addComponent(EmptySpace(TerminalSize(0,0)))
            testerPanel.addComponent(EmptySpace(TerminalSize(0,0)))
            mainPanel.addComponent(testerPanel.withBorder(Borders.singleLine("Panel " + (i + 1))), GridLayout.createHorizontallyFilledLayoutData())
        }

        logLabel.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT)
        logLabel.preferredSize = TerminalSize(80, 20)

        Logger.getLogger("").addHandler(LabelLogHandler(logLabel, 100))
        mainPanel.addComponent(logLabel.withBorder(Borders.singleLine("Log")), GridLayout.createHorizontallyFilledLayoutData(5))
        window.component = mainPanel
        // Create gui and start gui
        val gui = MultiWindowTextGUI(screen, DefaultWindowManager(), EmptySpace(TextColor.ANSI.BLACK))
        gui.addWindowAndWait(window)
    }
}
