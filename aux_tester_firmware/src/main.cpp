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

SlimeVR::Logging::Logger logger("SlimeVR");

unsigned long loopTime;
enum TestState {
    IMU_NOT_CONNECTED,
    IMU_WAITING_RESPONSE,
    IMU_WAITING_DISCONNECT
};
TestState state = TestState::IMU_NOT_CONNECTED;
Sensor *m_Sensor1;
unsigned long imuConnected;
int8_t foundIMUAddr = -1;

void setup()
{
    Serial.begin(serialBaudRate);

    Serial.println();
    Serial.println();
    Serial.println();

    logger.info("SlimeVR Extension Tester v" FIRMWARE_VERSION " starting up...");

    Wire.begin(static_cast<int>(PIN_IMU_SDA), static_cast<int>(PIN_IMU_SCL)); 

#ifdef ESP8266
    Wire.setClockStretchLimit(150000L); // Default stretch limit 150mS
#endif
#ifdef ESP32 // Counterpart on ESP32 to ClockStretchLimit
    Wire.setTimeOut(150);
#endif
    Wire.setClock(I2C_SPEED);

    logger.info("Waiting for first device...");
    loopTime = millis();
}

#define BNO_EXT_WRONG_ADDRESS 0x4a
#define BNO_EXT_ADDRESS 0x4b
#define IMU_TIMEOUT 1000

void printPass() {
    Serial.print("  _____           _____  _____\n"
                " |  __ \\  /\\     / ____|/ ____|\n"
                " | |__) |/  \\   | (___ | (___\n"  
                " |  ___// /\\ \\   \\___ \\ \\___ \\\n" 
                " | |   / ____ \\  ____) |____) |\n"
                " |_|  /_/    \\_\\|_____/|_____/\n");
}

void printFail() {
    Serial.print("  ______        _____  _\n"      
                " |  ____|/\\    |_   _|| |\n"     
                " | |__  /  \\     | |  | |\n"     
                " |  __|/ /\\ \\    | |  | |\n"     
                " | |  / ____ \\  _| |_ | |____\n" 
                " |_| /_/    \\_\\|_____||______|\n");
}

void loop()
{
    switch(state)
    {
        case IMU_NOT_CONNECTED:
            if(I2CSCAN::isI2CExist(BNO_EXT_ADDRESS))
            {
                foundIMUAddr = BNO_EXT_ADDRESS;
                logger.info("Found I2C device on 0x%02x", BNO_EXT_ADDRESS);
                delay(500); // Wait for it to boot
                m_Sensor1 = new BNO080Sensor(0, IMU, BNO_EXT_ADDRESS, IMU_ROTATION, PIN_IMU_INT_2);
                m_Sensor1->motionSetup();
                if(!m_Sensor1->isWorking())
                {
                    logger.fatal("Initialization failed.");
                    logger.fatal("Test failed!");
                    printFail();
                    logger.info("Waiting until device disconnected");
                    state = IMU_WAITING_DISCONNECT;
                }
                else
                {
                    state = IMU_WAITING_RESPONSE;
                    logger.info("Waiting for response from the IMU");
                    imuConnected = millis();
                }
            }
            foundIMUAddr = I2CSCAN::findI2CAddr();
            if(foundIMUAddr > 0 && foundIMUAddr != BNO_EXT_ADDRESS)
            {
                logger.fatal("Found I2C device on wrong address 0x%02x", foundIMUAddr);
                logger.fatal("Test failed!");
                printFail();
                logger.info("Waiting until device disconnected");
                state = IMU_WAITING_DISCONNECT;
            }
            break;
        case IMU_WAITING_RESPONSE:
            m_Sensor1->motionLoop();
            if(m_Sensor1->isWorking() && m_Sensor1->hadData)
            {
                logger.info(
                    "Sensor: %s (%.3f %.3f %.3f %.3f) is working: %s, had data: %s",
                    getIMUNameByType(m_Sensor1->getSensorType()),
                    UNPACK_QUATERNION(m_Sensor1->getQuaternion()),
                    m_Sensor1->isWorking() ? "true" : "false",
                    m_Sensor1->hadData ? "true" : "false"
                );
                logger.info("Test passed!");
                printPass();
                logger.info("Waiting until device disconnected");
                state = IMU_WAITING_DISCONNECT;
            }
            else if(millis() - imuConnected > IMU_TIMEOUT)
            {
                logger.info(
                    "Sensor: %s (%.3f %.3f %.3f %.3f) is working: %s, had data: %s",
                    getIMUNameByType(m_Sensor1->getSensorType()),
                    UNPACK_QUATERNION(m_Sensor1->getQuaternion()),
                    m_Sensor1->isWorking() ? "true" : "false",
                    m_Sensor1->hadData ? "true" : "false"
                );
                logger.info("Test failed by timeout");
                printFail();
                logger.info("Waiting until device disconnected");
                state = IMU_WAITING_DISCONNECT;
            }
            break;
        case IMU_WAITING_DISCONNECT:
            if(!I2CSCAN::isI2CExist(foundIMUAddr))
            {
                foundIMUAddr = -1;
                state = IMU_NOT_CONNECTED;
                logger.info("Device disconnected. Connect next device.");
                delay(500);
            }
        break;
    }

    unsigned long time = millis();
    if(time - loopTime < 10)
    {
        delay(10 - (time - loopTime));
    }
    loopTime = time;
}
