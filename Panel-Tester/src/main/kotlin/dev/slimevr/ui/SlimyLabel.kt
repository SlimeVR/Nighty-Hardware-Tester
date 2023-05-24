/*
 * This file is part of lanterna (https://github.com/mabe02/lanterna).
 *
 * lanterna is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2010-2020 Martin Berglund
 */
package dev.slimevr.ui

import com.googlecode.lanterna.SGR
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TerminalTextUtils
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.AbstractComponent
import com.googlecode.lanterna.gui2.ComponentRenderer
import com.googlecode.lanterna.gui2.TextGUIGraphics
import dev.slimevr.logger.LogManager
import java.util.*
import java.util.logging.Level

/**
 * Label is a simple read-only text display component. It supports customized colors and multi-line text.
 * @author Martin
 */
class SlimyLabel : AbstractComponent<SlimyLabel> {
    private var cachedLines = mutableListOf<String>()

    /**
     * Returns the foreground color used when drawing the label, or `null` if the color is read from the current
     * theme.
     * @return Foreground color used when drawing the label, or `null` if the color is read from the current
     * theme.
     */
    var foregroundColor: TextColor? = null
        private set

    /**
     * Returns the background color used when drawing the label, or `null` if the color is read from the current
     * theme.
     * @return Background color used when drawing the label, or `null` if the color is read from the current
     * theme.
     */
    var backgroundColor: TextColor? = null
        private set
    private val additionalStyles = EnumSet.noneOf(SGR::class.java)
    var scrollToBottom = true

    /**
     * Main constructor, creates a new Label displaying a specific text.
     * @param text Text the label will display
     */
    constructor(text: String) {
        this.text = text
    }

    @set:Synchronized
    private var lines = emptyArray<String>()
        set(lines) {
            field = lines
            this.cachedLines.clear()
        }

    @get:Synchronized
    @set:Synchronized
    var text: String
        /**
         * Returns the text this label is displaying. Multi-line labels will have their text concatenated with \n, even if
         * they were originally set using multi-line text having \r\n as line terminators.
         * @return String of the text this label is displaying
         */
        get() {
            if (lines.isEmpty()) {
                return ""
            }
            return lines.joinToString { "\n" }
        }
        /**
         * Updates the text this label is displaying
         * @param text New text to display
         */
        set(text) {
            lines = splitIntoMultipleLines(text)
            invalidate()
        }

    /**
     * Utility method for taking a string and turning it into an array of lines. This method is used in order to deal
     * with line endings consistently.
     * @param text Text to split
     * @return Array of strings that forms the lines of the original string
     */
    private fun splitIntoMultipleLines(text: String): Array<String> {
        return text.replace("\r", "").split("\n".toRegex()).dropLastWhile { it.isBlank() }.toTypedArray()
    }

    /**
     * Overrides the current theme's foreground color and use the one specified. If called with `null`, the
     * override is cleared and the theme is used again.
     * @param foregroundColor Foreground color to use when drawing the label, if `null` then use the theme's
     * default
     * @return Itself
     */
    @Synchronized
    fun setForegroundColor(foregroundColor: TextColor?): SlimyLabel {
        this.foregroundColor = foregroundColor
        return this
    }

    /**
     * Overrides the current theme's background color and use the one specified. If called with `null`, the
     * override is cleared and the theme is used again.
     * @param backgroundColor Background color to use when drawing the label, if `null` then use the theme's
     * default
     * @return Itself
     */
    @Synchronized
    fun setBackgroundColor(backgroundColor: TextColor?): SlimyLabel {
        this.backgroundColor = backgroundColor
        return this
    }

    /**
     * Adds an additional SGR style to use when drawing the label, in case it wasn't enabled by the theme
     * @param sgr SGR style to enable for this label
     * @return Itself
     */
    @Synchronized
    fun addStyle(sgr: SGR): SlimyLabel {
        additionalStyles.add(sgr)
        return this
    }

    /**
     * Removes an additional SGR style used when drawing the label, previously added by `addStyle(..)`. If the
     * style you are trying to remove is specified by the theme, calling this method will have no effect.
     * @param sgr SGR style to remove
     * @return Itself
     */
    @Synchronized
    fun removeStyle(sgr: SGR): SlimyLabel {
        additionalStyles.remove(sgr)
        return this
    }

    override fun createDefaultRenderer(): ComponentRenderer<SlimyLabel> {
        return object : ComponentRenderer<SlimyLabel> {
            override fun getPreferredSize(label: SlimyLabel): TerminalSize {
                return size
            }

            override fun drawComponent(graphics: TextGUIGraphics, component: SlimyLabel) {
                val themeDefinition = component.themeDefinition
                graphics.applyThemeStyle(themeDefinition.normal)
                if (foregroundColor != null) {
                    graphics.setForegroundColor(foregroundColor)
                }
                if (backgroundColor != null) {
                    graphics.setBackgroundColor(backgroundColor)
                }
                for (sgr in additionalStyles) {
                    graphics.enableModifiers(sgr)
                }
                if (component.lines.isEmpty())
                    return
                val availableColumns = graphics.size.columns
                if (availableColumns <= 1)
                    return
                if (cachedLines.isEmpty()) {
                    try {
                        cachedLines = TerminalTextUtils.getWordWrappedText(availableColumns, *component.lines)
                    } catch (t: Throwable) {
                        LogManager.onlyFileLogger.log(
                            Level.SEVERE,
                            "Funky stuff with lines:\n${component.lines.joinToString { "\n" }}\nThe exception is:"
                        )
                        throw t
                    }
                }
                val lines = graphics.size.rows.coerceAtMost(cachedLines.size)
                val start = if (scrollToBottom) 0.coerceAtLeast(cachedLines.size - lines) else 0
                for (row in start until start + lines) {
                    val line = cachedLines[row]
                    val fitString = TerminalTextUtils.fitString(line, availableColumns)
                    graphics.putString(0, row - start, fitString)
                }
            }
        }
    }
}
