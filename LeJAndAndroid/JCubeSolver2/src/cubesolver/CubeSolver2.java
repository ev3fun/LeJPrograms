package cubesolver;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import lejos.hardware.Bluetooth;
import lejos.hardware.Button;
import lejos.hardware.LED;
import lejos.hardware.ev3.LocalEV3;
import lejos.hardware.lcd.LCD;
import lejos.hardware.motor.EV3LargeRegulatedMotor;
import lejos.hardware.port.MotorPort;
import lejos.hardware.port.SensorPort;
import lejos.hardware.sensor.EV3UltrasonicSensor;
import lejos.remote.nxt.BTConnection;
import lejos.remote.nxt.BTConnector;
import lejos.remote.nxt.NXTConnection;
import lejos.robotics.SampleProvider;
import lejos.utility.Delay;

public class CubeSolver2 {

    private static DataOutputStream dataOut;
    private static DataInputStream dataIn;
    private static BTConnection BTLink;

    private static EV3LargeRegulatedMotor lm1;
    private static EV3LargeRegulatedMotor lm2;
    private static EV3UltrasonicSensor us;
    private static LED led;
    private static float lm1MaxSpeed;
    private static float lm2MaxSpeed;

    private static CubeAlgorithm ca;
    private static String logFilePath = "";
    private static String lastMessage = "";
    private static byte[] colorBuf = new byte[16];
    private static int turntablePosition = 0;
    private static int tiltOffset = 12;
    private static int turn_cut1 = 15;
    private static int turn_cut2 = 18;
    private static int turn_cut3 = -25;

    private static void removeLog() {
        File f = new File(logFilePath);
        if (f.exists()) {
            f.delete();
        }
    }

    private static void writeLog(String content) {
        FileWriter fw = null;
        try {
            File f = new File(logFilePath);
            fw = new FileWriter(f, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        PrintWriter pw = new PrintWriter(fw);
        pw.println(content);
        pw.flush();
        try {
            fw.flush();
            pw.close();
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void initDevices() {
        lm1 = new EV3LargeRegulatedMotor(MotorPort.A);
        lm2 = new EV3LargeRegulatedMotor(MotorPort.B);
        us = new EV3UltrasonicSensor(SensorPort.S1);
        lm1MaxSpeed = 850; // lm1.getMaxSpeed();
        lm2MaxSpeed = 850; // lm2.getMaxSpeed();
    }

    private static void closeDevices() {
        if (lm1 != null)
            lm1.close();
        if (lm2 != null)
            lm2.close();
        if (us != null)
            us.close();
    }

    private static void introMessage() {
        LCD.drawString("+----------------+", 0, 0);
        LCD.drawString("|  Cube Solver   |", 0, 1);
        LCD.drawString("+----------------+", 0, 2);
        LCD.drawString("Escape key to end.", 0, 7);
    }

    private static void drawMessage(String msg) {
        if (!lastMessage.equals(msg)) {
            LCD.drawString("                  ", 0, 4);
            LCD.drawString(msg, 0, 4);
            lastMessage = msg;
        }
    }

    private static void tiltAway() {
        lm1.setSpeed(lm1MaxSpeed * 70 / 100);
        lm1.rotateTo(10);
    }

    private static void resetTilt() {
        drawMessage("Reset Tilt");
        lm1.setSpeed(lm1MaxSpeed * 20 / 100);
        lm1.backward();
        int oldp = 0, newp = 1;
        while (oldp != newp) {
            oldp = lm1.getTachoCount();
            Delay.msDelay(200);
            newp = lm1.getTachoCount();
        }
        lm1.stop();
        lm1.resetTachoCount();
        tiltAway();
    }

    private static void resetRGB() {
        drawMessage("Reset RGB");
        lm2.setSpeed(lm2MaxSpeed * 70 / 100);
        lm2.backward();
        for (int i = 0; i < 10; i++) {
            // scanRGB();
        }
        lm2.stop();
        lm2.setSpeed(lm2MaxSpeed * 75 / 100);
        lm2.rotateTo(turntablePosition);
    }

    private static void resetDevices() {
        // red flash
        led.setPattern(5);
        resetTilt();
        resetRGB();
        drawMessage("Ready");
    }

    private static void tiltHold() {
        lm1.setSpeed(lm1MaxSpeed * 60 / 100);
        lm1.rotateTo(tiltOffset + 85);
    }

    private static void tilt(int num) {
        if (lm1.getTachoCount() < 60) {
            tiltHold();
        }
        for (int i = 0; i < num; i++) {
            if (i > 0) {
                Delay.msDelay(500);
            }
            // action 1
            lm1.setSpeed(lm1MaxSpeed);
            lm1.rotateTo(tiltOffset + 195);
            // action 2
            lm1.setSpeed(lm1MaxSpeed * 75 / 100);
            lm1.rotate(-15, true);
            Delay.msDelay(70);
            // action 3
            lm1.setSpeed(lm1MaxSpeed * 75 / 100);
            lm1.rotateTo(tiltOffset + 60);
            tiltHold();
        }
    }

    // m -> rotate 90*m
    private static void spin(int m) {
        if (15 - lm1.getTachoCount() < 0) {
            lm1.setSpeed(lm1MaxSpeed * 75 / 100);
            lm1.rotateTo(15);
        }
        turntablePosition = turntablePosition - (m * 90 * 3);
        lm2.setSpeed(lm2MaxSpeed);
        lm2.rotateTo(turntablePosition);
    }

    private static boolean scanFace(int f, byte color[]) {
        tiltAway();
        boolean ret = sendScanCommand();
        if (ret) {
            int len = getScanResult(colorBuf);
            Delay.msDelay(1000);
            if (len == 10) {
                System.arraycopy(colorBuf, 0, color, f * 9, 9);
            } else {
                return false;
            }
        } else {
            return false;
        }
        int pos = turntablePosition - lm2.getTachoCount();
        if (pos > 0) {
            turntablePosition = turntablePosition - (pos - pos % (360 * 3));
        }
        lm2.setSpeed(lm2MaxSpeed * 75 / 100);
        lm2.rotateTo(turntablePosition);
        return true;
    }

    private static boolean checkColor(byte[] color) {
        int i = 0;
        int count_y = 0, count_w = 0, count_b = 0, count_g = 0, count_r = 0, color_sum = 0;
        for (i = 0; i < 54; i++) {
            switch (color[i]) {
            case 'Y':
                count_y++;
                break;
            case 'W':
                count_w++;
                break;
            case 'B':
                count_b++;
                break;
            case 'G':
                count_g++;
                break;
            case 'R':
                count_r++;
                break;
            }
        }
        try {
            writeLog(new String(color, "ASCII"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (i = 0; i < 46; i += 9) {
            if (color[i] == 'Y') {
                color_sum += 1;
            } else if (color[i] == 'W') {
                color_sum += 2;
            } else if (color[i] == 'B') {
                color_sum += 3;
            } else if (color[i] == 'G') {
                color_sum += 4;
            } else if (color[i] == 'R') {
                color_sum += 5;
            } else {
                color_sum += 6;
            }
        }
        if (color_sum != 21 || count_y != 9 || count_w != 9 || count_b != 9 || count_g != 9 || count_r != 9) {
            return false;
        }
        return true;
    }

    private static boolean scanCube(byte color[]) {
        for (int i = 0; i < 54; i++) {
            color[i] = 'O';
        }
        drawMessage("Scan the cube");
        scanFace(0, color);
        tilt(1);
        scanFace(1, color);
        tilt(1);
        scanFace(2, color);
        spin(1);
        Delay.msDelay(100);
        tilt(1);
        scanFace(3, color);
        spin(-1);
        Delay.msDelay(100);
        tilt(1);
        scanFace(4, color);
        tilt(1);
        scanFace(5, color);
        return checkColor(color);
    }

    private static void turn(int m_step) {
        tiltHold();
        int turn_cut = turn_cut3;
        if (m_step == 1) {
            turn_cut = turn_cut1;
        } else if (m_step == 2) {
            turn_cut = turn_cut2;
        }
        turntablePosition = turntablePosition - (m_step * 90 * 3);
        lm2.setSpeed(lm2MaxSpeed);
        lm2.rotateTo(turntablePosition - turn_cut);
        Delay.msDelay(100);
        lm2.rotateTo(turntablePosition);
    }

    private static void applyMoves(byte[] movebytes) {
        for (int i = 0; i < movebytes.length; i += 2) {
            int m_step = 0, n_step = 0;
            if (movebytes[i + 1] == '1') {
                m_step = 1;
                n_step = 1;
            } else if (movebytes[i + 1] == '2') {
                m_step = 2;
                n_step = 2;
            } else {
                m_step = -1;
                n_step = 3;
            }
            Delay.msDelay(50);
            if (movebytes[i] == 'S') {
                spin(m_step);
            } else if (movebytes[i] == 'R') {
                tilt(n_step);
            } else {
                turn(m_step);
            }
        }
    }

    private static void scanAndSolve() {
        int retryNum = 1;
        byte[] color = new byte[54];
        // orange flash
        led.setPattern(6);
        for (int i = 0; i < retryNum; i++) {
            if (scanCube(color)) {
                break;
            } else {
                if (i < (retryNum - 1)) {
                    drawMessage("Scan error, retry");
                } else {
                    drawMessage("Scan failed!");
                    Delay.msDelay(3000);
                    return;
                }
            }
        }
        drawMessage("Solving...");
        String moveStr;
        try {
            moveStr = ca.setInput(color).solve();
            writeLog(moveStr);
        } catch (Exception e) {
            drawMessage("Solve failed!");
            Delay.msDelay(3000);
            return;
        }
        // green flash
        led.setPattern(4);
        applyMoves(moveStr.getBytes());
        drawMessage("Solved!");
        // rotate 2 round
        tiltAway();
        spin(8);
    }

    public static boolean sendScanCommand() {
        try {
            dataOut.writeByte('c');
            dataOut.flush();
        } catch (IOException e) {
            writeLog("IO Exception: " + e.getMessage());
            return false;
        }
        return true;
    }

    public static int getScanResult(byte[] colors) {
        int len = 0;
        try {
            len = dataIn.read(colors);
        } catch (IOException e) {
            writeLog("IO Exception: " + e.getMessage());
            len = 0;
        }
        return len;
    }

    public static void main(String[] args) {
        try {
            boolean done = true;
            logFilePath = CubeSolver2.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            int index = logFilePath.lastIndexOf(".");
            logFilePath = logFilePath.substring(0, index + 1);
            logFilePath += "log";
            removeLog();
            // connect BT
            LCD.drawString("connect wait", 0, 2);
            BTConnector ncc = (BTConnector) Bluetooth.getNXTCommConnector();
            BTLink = (BTConnection) ncc.waitForConnection(30000, NXTConnection.RAW);
            if (BTLink == null) {
                drawMessage("BT connect fail.");
                Delay.msDelay(5000);
                return;
            }
            dataOut = BTLink.openDataOutputStream();
            dataIn = BTLink.openDataInputStream();
            // print information
            introMessage();
            // new Cube Algorithm engine
            ca = new CubeAlgorithm();
            // get LED control
            led = LocalEV3.ev3.getLED();

            // initial Devices
            initDevices();
            resetDevices();
            SampleProvider dsMode = us.getDistanceMode();
            int dsSize = dsMode.sampleSize();
            float[] dsSample = new float[dsSize];
            while (!Button.ESCAPE.isDown()) {
                dsMode.fetchSample(dsSample, 0);
                if ((dsSample[0] >= 0.06) && (dsSample[0] <= 0.10)) {
                    if (done) {
                        drawMessage("Remove the cube!");
                    } else {
                        Delay.msDelay(3000);
                        dsMode.fetchSample(dsSample, 0);
                        if ((dsSample[0] >= 0.06) && (dsSample[0] <= 0.10)) {
                            scanAndSolve();
                            done = true;
                        } else {
                            drawMessage("Insert a cube!");
                            // orange on
                            led.setPattern(3);
                            done = false;
                        }
                    }
                } else {
                    drawMessage("Insert a cube!");
                    // orange on
                    led.setPattern(3);
                    done = false;
                }
                Delay.msDelay(50);
            }
            led.setPattern(0);
            closeDevices();
        } catch (Exception e) {
            closeDevices();
            e.printStackTrace();
            Delay.msDelay(5000);
        }
    }

}
