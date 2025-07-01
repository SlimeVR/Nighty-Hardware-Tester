/*
    SlimeVR Code is placed under the MIT license
    Copyright (c) 2024 Eiren Rain & SlimeVR contributors

    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in
    all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
    THE SOFTWARE.
*/

#include "GlobalVars.h"
#include "Wire.h"
#include "globals.h"
#include <i2cscan.h>
#include "logging/Logger.h"
#include "sensors/bno080sensor.h"
#include <Adafruit_MCP23X17.h>
#include "PCA9547.h"
#include <CmdCallback.hpp>
#include <memory>
#include "sensorinterface/MCP23X17PinInterface.h"
#include "sensorinterface/I2CWireSensorInterface.h"
#include "sensors/softfusion/drivers/icm45686.h"
#include "sensorinterface/SensorInterface.h"
#include "sensorinterface/SensorInterfaceManager.h"
#include "sensorinterface/i2cimpl.h"
#include "sensors/softfusion/softfusionsensor.h"

#if USE_RUNTIME_CALIBRATION
#include "sensors/softfusion/runtimecalibration/RuntimeCalibration.h"
#define SFCALIBRATOR SlimeVR::Sensors::RuntimeCalibration::RuntimeCalibrator
#else
#include "sensors/softfusion/SoftfusionCalibration.h"
#define SFCALIBRATOR SlimeVR::Sensors::SoftfusionCalibrator
#endif

Timer<> globalTimer;
SlimeVR::Logging::Logger logger("SlimeVR");
SlimeVR::Sensors::SensorManager sensorManager;
SlimeVR::LEDManager ledManager;
SlimeVR::Status::StatusManager statusManager;
SlimeVR::Configuration::Configuration configuration;
SlimeVR::Network::Manager networkManager;
SlimeVR::Network::Connection networkConnection;

using ICM45686 = SlimeVR::Sensors::SoftFusion::Drivers::ICM45686;

#define IMU_TIMEOUT 300
#define INT_PIN 10
#define PCAADDR 0x70
#define BUTTON_PIN 1
#define I2C_SDA 1
#define I2C_SCL 0
#define LED_RED 3
#define LED_GREEN 4

unsigned long imuConnected;
Sensor *m_Sensor1;
int8_t foundIMUAddr = -1;
Adafruit_MCP23X17 mcp;
CmdCallback<1> cmdCallbacks;
CmdParser cmdParser;
CmdBuffer<256> cmdBuffer;
bool start = false;
bool justStarted = false;

struct ImuTestSettings
{
    bool i2cSelectEnabled;
    uint8_t i2cSelectChannel;
    uint8_t i2cAddress;
    uint8_t intPinGP;
    std::unique_ptr<PinInterface> intPin;
};

ImuTestSettings imus[] = {
    {false, 0, (ICM45686::Address) ^ 0x02, 6, nullptr},
    {false, 0, (ICM45686::Address + 1) ^ 0x02, 2, nullptr},
    {true, 0, ICM45686::Address, 8, nullptr},
    {true, 0, ICM45686::Address + 1, 9, nullptr},
    {true, 1, ICM45686::Address, 10, nullptr},
    {true, 1, ICM45686::Address + 1, 11, nullptr},
    {true, 2, ICM45686::Address, 12, nullptr},
    {true, 2, ICM45686::Address + 1, 13, nullptr},
    {true, 3, ICM45686::Address, 14, nullptr},
    {true, 3, ICM45686::Address + 1, 1, nullptr},
};

uint8_t i2cSelectorChannel(uint8_t ch)
{
    if (ch > 3)
        return 255U;
    Wire.beginTransmission(PCAADDR);
    Wire.write(1 << ch);
    return Wire.endTransmission();
}

uint8_t i2cSelectorDisable()
{
    Wire.beginTransmission(PCAADDR);
    Wire.write(0);
    return Wire.endTransmission();
}

void updateCommands()
{
    cmdCallbacks.updateCmdProcessing(&cmdParser, &cmdBuffer, &Serial);
}

void cmdStart(CmdParser *parser)
{
    start = true;
}

void setup()
{
    delay(5000);
    Serial.begin(serialBaudRate);

    Serial.println();
    Serial.println();
    Serial.println();

    logger.info("SlimeVR Extension Tester v" FIRMWARE_VERSION " starting up...");

    I2CSCAN::clearBus(I2C_SDA, I2C_SCL); // Make sure the bus isn't stuck when resetting ESP without powering it down
    // Fixes I2C issues for certain IMUs. Previously this feature was enabled for selected IMUs, now it's enabled for all.
    // If some IMU turned out to be broken by this, check needs to be re-added.

    // join I2C bus
#if ESP32
    // For some unknown reason the I2C seem to be open on ESP32-C3 by default. Let's just close it before opening it again. (The ESP32-C3 only has 1 I2C.)
    Wire.end();
#endif
    Wire.begin(I2C_SDA, I2C_SCL);

#ifdef ESP8266
    Wire.setClockStretchLimit(150000L); // Default stretch limit 150mS
#endif
#ifdef ESP32 // Counterpart on ESP32 to ClockStretchLimit
    // Wire.setTimeOut(150);
#endif
    Wire.setClock(I2C_SPEED);

    if (!mcp.begin_I2C())
    {
        while (1)
        {
            logger.error("MCP initialization error.");
            I2CSCAN::scani2cports();
            delay(1000);
        }
    }

    pinMode(INT_PIN, INPUT);
    mcp.setupInterrupts(true, false, LOW);
    mcp.pinMode(LED_RED, OUTPUT);
    mcp.pinMode(LED_GREEN, OUTPUT);
    mcp.pinMode(BUTTON_PIN, INPUT_PULLUP);
    mcp.digitalWrite(LED_RED, LOW);
    mcp.digitalWrite(LED_GREEN, LOW);
    cmdCallbacks.addCmd("START", &cmdStart);

    for (uint8_t i = 0; i < 10; i++)
    {
        ImuTestSettings *test = &imus[i];
        mcp.pinMode(test->intPinGP, INPUT_PULLUP);
        test->intPin = std::make_unique<MCP23X17PinInterface>(&mcp, test->intPinGP);
    }

    logger.info("Boot complete, awaiting button interrupt at A1");
}

void checkIfTrackersConnected()
{
    bool found = false;
    for (uint8_t i = 0; i < 10; i++)
    {
        ImuTestSettings *test = &imus[i];
        if (test->i2cSelectEnabled)
        {
            i2cSelectorChannel(test->i2cSelectChannel);
        }
        else
        {
            i2cSelectorDisable();
        }
        Wire.end();
        Wire.begin(I2C_SDA, I2C_SCL);
        if (I2CSCAN::hasDevOnBus(test->i2cAddress))
        {
            found = true;
            break;
        }
    }
    if (found)
    {
        if (!justStarted)
        {
            logger.info("Tracker connection auto-detected, starting test automatically");
            start = true;
        }
    }
    else
    {
        justStarted = false;
    }
}

void loop()
{
    SlimeVR::I2CWireSensorInterface sensorInterface = SlimeVR::I2CWireSensorInterface(I2C_SDA, I2C_SCL);
    sensorInterface.init();
    updateCommands();
    checkIfTrackersConnected();
    // enable interrupt on button_pin
    if (!digitalRead(INT_PIN) || start)
    {
        logger.info("Interrupt detected on pin %d", mcp.getLastInterruptPin());
        if (!mcp.digitalRead(BUTTON_PIN) || start)
        {
            bool testFailed = false;
            mcp.digitalWrite(LED_RED, LOW);
            mcp.digitalWrite(LED_GREEN, LOW);
            start = false;
            justStarted = true;
            logger.info("Button pressed");
            delay(250); // debounce
            if (start)
                delay(1000);       // Wait good connection
            mcp.clearInterrupts(); // clear
            logger.info("Scanning ICM-45686s...");
            for (uint8_t i = 0; i < 10; i++)
            {
                bool imuFailed = false;
                ImuTestSettings *test = &imus[i];
                logger.info("[%d] Scanning: %s %d %d %d", i, (test->i2cSelectEnabled ? "true" : "false"), test->i2cSelectChannel, test->i2cAddress, test->intPinGP);
                if (test->i2cSelectEnabled)
                {
                    i2cSelectorChannel(test->i2cSelectChannel);
                }
                else
                {
                    i2cSelectorDisable();
                }
#if ESP32
                Wire.end();
                Wire.begin(I2C_SDA, I2C_SCL);
#endif
                delay(50);
                if (I2CSCAN::hasDevOnBus(test->i2cAddress))
                {
                    logger.info("[%d] I2C Found", i);
                    // m_Sensor1 = new BNO080Sensor(i, IMU, test->i2cAddress, IMU_ROTATION, test->intPin.get());
                    SlimeVR::Sensors::I2CImpl imuInterface = SlimeVR::Sensors::I2CImpl(test->i2cAddress);
                    m_Sensor1 = new SlimeVR::Sensors::SoftFusionSensor<SlimeVR::Sensors::SoftFusion::Drivers::ICM45686, SFCALIBRATOR>(i, imuInterface, IMU_ROTATION, &sensorInterface, test->intPin.get(), 0);
                    m_Sensor1->motionSetup();
                    if (!m_Sensor1->isWorking())
                    {
                        logger.fatal("[%d] Initialization failed.", i);
                        logger.fatal("[%d] Test failed!", i);
                        testFailed = true;
                        imuFailed = true;
                    }
                    else
                    {
                        logger.info("[%d] Waiting for response from the IMU", i);
                        imuConnected = millis();
                    }
                }
                else
                {
                    logger.fatal("[%d] I2C not found.", i);
                    logger.fatal("[%d] Test failed!", i);
                    testFailed = true;
                    imuFailed = true;
                }
                while (!imuFailed)
                {
                    m_Sensor1->motionLoop();
                    if (m_Sensor1->isWorking() && m_Sensor1->getHadData())
                    {
                        logger.info(
                            "[%d] Sensor: %s (%.3f %.3f %.3f %.3f) is working: %s, had data: %s",
                            i,
                            getIMUNameByType(m_Sensor1->getSensorType()),
                            UNPACK_QUATERNION(m_Sensor1->getFusedRotation()),
                            m_Sensor1->isWorking() ? "true" : "false",
                            m_Sensor1->getHadData() ? "true" : "false");
                        const char *mag = m_Sensor1->getAttachedMagnetometer();
                        if (mag)
                        {
                            logger.info("[%d] Mag: %s", i, mag);
                            logger.info("[%d] Test passed!", i);
                        }
                        else
                        {
                            logger.info("[%d] Test failed, no mag found", i);
                        }
                        break;
                    }
                    else if (millis() - imuConnected > IMU_TIMEOUT)
                    {
                        logger.info(
                            "[%d] Sensor: %s (%.3f %.3f %.3f %.3f) is working: %s, had data: %s",
                            i,
                            getIMUNameByType(m_Sensor1->getSensorType()),
                            UNPACK_QUATERNION(m_Sensor1->getFusedRotation()),
                            m_Sensor1->isWorking() ? "true" : "false",
                            m_Sensor1->getHadData() ? "true" : "false");
                        logger.info("[%d] Test failed by timeout", i);
                        testFailed = true;
                        imuFailed = true;
                        break;
                    }
                    delay(10);
                }
                delay(50);
            }
            if (testFailed)
            {
                mcp.digitalWrite(LED_RED, HIGH);
                logger.fatal("Test failed");
            }
            else
            {
                mcp.digitalWrite(LED_GREEN, HIGH);
                logger.info("Test passed");
            }
        }
    }
    delay(100);
}
