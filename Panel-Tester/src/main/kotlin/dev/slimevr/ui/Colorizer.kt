package dev.slimevr.ui

import com.googlecode.lanterna.TextColor

object Colorizer {

    fun toForegroundString(color: TextColor) : String {
        return "\u001b[${color.foregroundSGRSequence.toString(Charsets.US_ASCII)}m"
    }

    fun toBackgroundString(color: TextColor) : String {
        return "\u001b[${color.foregroundSGRSequence.toString(Charsets.US_ASCII)}m"
    }
}
