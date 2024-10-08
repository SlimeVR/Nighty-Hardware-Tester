package dev.slimevr.testing

import com.googlecode.lanterna.TextColor

enum class TestStatus(
    val color: TextColor
) {
    DISCONNECTED(TextColor.ANSI.WHITE),
    TESTING(TextColor.ANSI.YELLOW),
    ERROR(TextColor.ANSI.RED_BRIGHT),
    PASS(TextColor.ANSI.GREEN_BRIGHT),
    RETESTED(TextColor.ANSI.BLUE_BRIGHT),
    PORT_ERROR(TextColor.ANSI.CYAN_BRIGHT),
    NOT_UPDATED(TextColor.ANSI.MAGENTA_BRIGHT)

}
