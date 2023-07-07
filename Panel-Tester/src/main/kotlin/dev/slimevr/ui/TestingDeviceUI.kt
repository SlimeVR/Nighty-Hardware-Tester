package dev.slimevr.ui

import com.googlecode.lanterna.gui2.EmptySpace
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.Panel

data class TestingDeviceUI(
    var panelNumber: Int,
    var panel: Panel,
    var statusColorPanel: EmptySpace,
    var idLabel: Label,
    var usbLabel: Label
)
