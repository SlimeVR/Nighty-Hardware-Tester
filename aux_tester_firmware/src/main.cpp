/*
    SlimeVR Code is placed under the MIT license
    Copyright (c) 2021 Eiren Rain & SlimeVR contributors

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

#include "Wire.h"
#include "globals.h"
#include <i2cscan.h>
#include "logging/Logger.h"
#include "sensors/bno080sensor.h"
#include <Adafruit_MCP23X17.h>
#include "PCA9547.h"

#define IMU_TIMEOUT 300
#define INT_PIN 10
#define PCAADDR 0x70
#define BUTTON_PIN 1
#define I2C_SDA 1
#define I2C_SCL 0

SlimeVR::Logging::Logger logger("SlimeVR");

unsigned long imuConnected;
Sensor *m_Sensor1;
int8_t foundIMUAddr = -1;
Adafruit_MCP23X17 mcp;

struct ImuTestSettings {
    bool i2cSelectEnabled;
    uint8_t i2cSelectChannel;
    uint8_t i2cAddress;
    uint8_t intPinGP;
};

ImuTestSettings imus[] = {
    {false, 0, 0x4a ^ 0x02, 6},
    {false, 0, 0x4b ^ 0x02, 7},
    {true, 0, 0x4a, 8},
    {true, 0, 0x4b, 9},
    {true, 1, 0x4a, 10},
    {true, 1, 0x4b, 11},
    {true, 2, 0x4a, 12},
    {true, 2, 0x4b, 13},
    {true, 3, 0x4a, 14},
    {true, 3, 0x4b, 15},
};

uint8_t i2cSelectorChannel(uint8_t ch) {
    if (ch > 3)
        return 255U;
    Wire.beginTransmission(PCAADDR);
    Wire.write(1 << ch);
    return Wire.endTransmission();
}


uint8_t i2cSelectorDisable() {
    Wire.beginTransmission(PCAADDR);
    Wire.write(0);
    return Wire.endTransmission();
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
    //Wire.setTimeOut(150);
#endif
    Wire.setClock(I2C_SPEED);

    if (!mcp.begin_I2C()) {
        while (1) {
            logger.error("MCP initialization error.");
            I2CSCAN::scani2cports();
            delay(1000);
        }
    }

    pinMode(INT_PIN, INPUT);
    mcp.setupInterrupts(true, false, LOW);

    logger.info("Boot complete, awaiting button interrupt at A1");
}

void printPass(uint8_t boardId = 0) {
    Serial.printf("[%d] \033[32;42mPASS[0m\n", boardId);
}

void printFail(uint8_t boardId = 0) {
    Serial.printf("[%d] \033[31;41mFAIL[0m\n", boardId);
}

void loop()
{
    bool testFailed = false;
    // enable interrupt on button_pin
    mcp.setupInterruptPin(BUTTON_PIN, LOW);
    mcp.pinMode(BUTTON_PIN, INPUT_PULLUP);
    if (!digitalRead(INT_PIN))
    {
        logger.info("Interrupt detected on pin %d", mcp.getLastInterruptPin());
        if(!mcp.digitalRead(BUTTON_PIN))
        {
            mcp.disableInterruptPin(BUTTON_PIN);
            logger.info("Button pressed");
            delay(250);  // debounce
            mcp.clearInterrupts();  // clear
            logger.info("Scanning BNO085s...");
            for(uint8_t i = 0; i < 10; i++)
            {
                bool imuFailed = false;
                ImuTestSettings test = imus[i];
                logger.info("[%d] Scanning: %s %d %d %d", i, (test.i2cSelectEnabled ? "true" : "false"), test.i2cSelectChannel, test.i2cAddress, test.intPinGP);
                mcp.setupInterruptPin(test.intPinGP, LOW);
                if(test.i2cSelectEnabled)
                {
                    i2cSelectorChannel(test.i2cSelectChannel);
                }
                else
                {
                    i2cSelectorDisable();
                }
                Wire.end();
                Wire.begin(I2C_SDA, I2C_SCL);
                if(I2CSCAN::isI2CExist(test.i2cAddress))
                {
                    logger.info("[%d] I2C Found", i);
                    m_Sensor1 = new BNO080Sensor(i, IMU, test.i2cAddress, IMU_ROTATION, INT_PIN);
                    m_Sensor1->motionSetup();
                    if(!m_Sensor1->isWorking())
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
                while(!imuFailed)
                {
                    m_Sensor1->motionLoop();
                    if(m_Sensor1->isWorking() && m_Sensor1->hadData)
                    {
                        logger.info(
                            "[%d] Sensor: %s (%.3f %.3f %.3f %.3f) is working: %s, had data: %s",
                            i,
                            getIMUNameByType(m_Sensor1->getSensorType()),
                            UNPACK_QUATERNION(m_Sensor1->getQuaternion()),
                            m_Sensor1->isWorking() ? "true" : "false",
                            m_Sensor1->hadData ? "true" : "false"
                        );
                        logger.info("[%d] Test passed!", i);
                        break;
                    }
                    else if(millis() - imuConnected > IMU_TIMEOUT)
                    {
                        logger.info(
                            "[%d] Sensor: %s (%.3f %.3f %.3f %.3f) is working: %s, had data: %s",
                            i,
                            getIMUNameByType(m_Sensor1->getSensorType()),
                            UNPACK_QUATERNION(m_Sensor1->getQuaternion()),
                            m_Sensor1->isWorking() ? "true" : "false",
                            m_Sensor1->hadData ? "true" : "false"
                        );
                        logger.info("[%d] Test failed by timeout", i);
                        testFailed = true;
                        imuFailed = true;
                        break;
                    }
                    delay(10);
                }
                mcp.disableInterruptPin(test.intPinGP);
                delay(100);
            }
        }
    }
    delay(1000);
    logger.info("Awaiting interrupts...");
}