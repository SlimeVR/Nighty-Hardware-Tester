Butterfly Tracker Tester hardware configuration, deploy and debugging.

=== Target hardware setup ===

1. Flash OS Image to SD card
	Target hardware: RPi4b
	RPi OS: Legacy 64-bit Lite (Debian Bookworm)
	Username: rpi4
	Add ssh key

Useful commands:
	sudo raspi-config
	cat /etc/os-release
	uname -m

2. Install Java (should be same major version as JVM toolchain for embedded debugging):
	sudo apt install -y i2c-tools vim git java-common libxi6 libxrender1 libxtst6
	curl -s "https://get.sdkman.io" | bash
	source "$HOME/.sdkman/bin/sdkman-init.sh"
	sdk update
	sdk list java
	sdk install java 17.0.19-zulu
	sdk use java 17.0.19-zulu
	sudo update-alternatives --install "/usr/bin/java" "java" "/home/rpi4/.sdkman/candidates/java/17.0.19-zulu/bin/java" 1

3. Install OpenOCD + Screen:
	sudo apt-get install openocd screen
	
4. Use raspi-config to:
	Enable I2C, SPI, GPIO control
	Enable Auto-login
	
5. Deploy shells, configs and gradle dependency jars to `~/panel_tester`
	
6. Configure autorun with `sudo nano ~/.bashrc`, append next lines:
	cd panel_tester/
	sh run.sh
	
7. Upload Full image to `~/panel_tester`.
7.1. Check the commit hash of the app image build.
It can be lookup by Serial command `info` from the flashed tracker.
7.2. Full image can be combined by mergehex (`C:\Program Files\Nordic Semiconductor\nrf-command-line-tools\bin\mergehex.exe` on Windows).
You will need bootloader.hex and skip_image_crc_nrf52833.hex to combine with the app firmware hex.
Merge bootloader with app image and skip image CRC fix:
	mergehex.exe -m bootloader.hex zephyr.hex skip_image_crc_nrf52833.hex -o zephyr_full.hex

8. Configure run.sh (debug.sh)
	TESTER_RPC_URL - API URL
	TESTER_RPC_PASSWORD - API password
	TESTER_REPORT_TYPE - DB report type
	TESTER_NAME - hostname
	TESTER_STAGE=5 - for Butterfly Tracker test
	TESTER_FIRMWARE_FILE - hex filename
	TESTER_FIRMWARE_COMMIT - substring of an app build version including the commit hash (APP_BUILD_VERSION)

IMPORTANT: All shell scripts should be with Unix style EOL
	
=== Development and debugging ===

1. Install extensions to VSCode
	Kotlin by JetBrains
	Gradle for Java
	Kotlin Debug

3. Put files from `Panel-Tester/deploy/dot_vscode` to `Panel-Tester/.vscode`

4. Use `Upload to Remote` launch to build and deploy to the target host

5. `Remote Launch and Debug` can be used to embedded debugging on the target. But it is EXTREMELY slow.
