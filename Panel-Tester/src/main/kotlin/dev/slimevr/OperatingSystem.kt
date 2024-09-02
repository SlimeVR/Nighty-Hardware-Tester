package dev.slimevr

import java.io.File
import java.util.*

enum class OperatingSystem(
	val systemName: String,
	private val aliases: Array<String?>
) {

	LINUX("linux", arrayOf("linux", "unix")),
	WINDOWS("windows", arrayOf("win")),
	OSX("osx", arrayOf("mac")),
	UNKNOWN("unknown", arrayOfNulls(0));

	companion object {

		private var currentPlatform: OperatingSystem? = null

		fun getJavaExecutable(forceConsole: Boolean): String {
			val separator = System.getProperty("file.separator")
			val path = System.getProperty("java.home") + separator + "bin" + separator
			if (getCurrentPlatform() == WINDOWS) {
				if (!forceConsole && File(path + "javaw.exe").isFile)
					return path + "javaw.exe"
				return path + "java.exe"
			}
			return path + "java"
		}

		@OptIn(ExperimentalStdlibApi::class)
		fun getCurrentPlatform(): OperatingSystem? {
			if (currentPlatform != null) return currentPlatform
			val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
			for (os in OperatingSystem.values()) {
				for (alias in os.aliases) {
					if (osName.contains(alias!!)) return os.also {
						currentPlatform = it
					}
				}
			}
			return UNKNOWN
		}

		val tempDirectory: String
			get() {
				if (currentPlatform == LINUX) {
					val tmp = System.getenv("XDG_RUNTIME_DIR")
					if (tmp != null) return tmp
				}
				return System.getProperty("java.io.tmpdir")
			}
	}
}
