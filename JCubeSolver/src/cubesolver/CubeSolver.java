package cubesolver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import lejos.hardware.Button;
import lejos.hardware.LED;
import lejos.hardware.ev3.LocalEV3;
import lejos.hardware.lcd.LCD;
import lejos.hardware.motor.EV3LargeRegulatedMotor;
import lejos.hardware.motor.EV3MediumRegulatedMotor;
import lejos.hardware.port.MotorPort;
import lejos.hardware.port.SensorPort;
import lejos.hardware.sensor.EV3ColorSensor;
import lejos.hardware.sensor.EV3UltrasonicSensor;
import lejos.robotics.SampleProvider;
import lejos.utility.Delay;

public class CubeSolver {

    private static EV3LargeRegulatedMotor lm1;
    private static EV3LargeRegulatedMotor lm2;
    private static EV3MediumRegulatedMotor mm;
    private static EV3UltrasonicSensor us;
    private static EV3ColorSensor cs;
    private static LED led;
    private static float lm1MaxSpeed;
    private static float lm2MaxSpeed;
    private static float mmMaxSpeed;

    private static CubeAlgorithm ca;
    private static String logFilePath = "";
    private static String lastMessage = "";
    private static int[] rgb = new int[3];
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
        mm = new EV3MediumRegulatedMotor(MotorPort.C);
        us = new EV3UltrasonicSensor(SensorPort.S1);
        cs = new EV3ColorSensor(SensorPort.S2);
        lm1MaxSpeed = 850; // lm1.getMaxSpeed();
        lm2MaxSpeed = 850; // lm2.getMaxSpeed();
        mmMaxSpeed = 1280; // mm.getMaxSpeed();
    }

    private static void closeDevices() {
        if (lm1 != null)
            lm1.close();
        if (lm2 != null)
            lm2.close();
        if (mm != null)
            mm.close();
        if (us != null)
            us.close();
        if (cs != null)
            cs.close();
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

    private static void scanAway() {
        mm.setSpeed(mmMaxSpeed);
        mm.rotateTo(-340);
    }

    private static void resetScan() {
        drawMessage("Reset Scan");
        mm.setSpeed(mmMaxSpeed * 40 / 100);
        mm.forward();
        int oldp = 0, newp = 1;
        while (oldp != newp) {
            oldp = mm.getTachoCount();
            Delay.msDelay(200);
            newp = mm.getTachoCount();
        }
        mm.stop();
        mm.rotate(-100);
        mm.setSpeed(mmMaxSpeed * 20 / 100);
        mm.forward();
        oldp = newp + 1;
        while (oldp != newp) {
            oldp = mm.getTachoCount();
            Delay.msDelay(500);
            newp = mm.getTachoCount();
        }
        mm.stop();
        mm.resetTachoCount();
        scanAway();
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

    private static void scanRGB() {
        SampleProvider redMode = cs.getRedMode();
        int redSize = redMode.sampleSize();
        float[] redSample = new float[redSize];
        redMode.fetchSample(redSample, 0);
        Delay.msDelay(10);
        SampleProvider rgbMode = cs.getRGBMode();
        int rgbSize = rgbMode.sampleSize();
        float[] rgbSample = new float[rgbSize];
        Delay.msDelay(1);
        rgbMode.fetchSample(rgbSample, 0);
        Delay.msDelay(10);
        for (int i = 0; i < 2; i++) {
            rgbMode.fetchSample(rgbSample, 0);
            Delay.msDelay(1);
        }
        rgb[0] = (int) (rgbSample[0] * 1000);
        rgb[1] = (int) (rgbSample[1] * 1000);
        rgb[2] = (int) (rgbSample[2] * 1000);
    }

    private static void resetRGB() {
        drawMessage("Reset RGB");
        lm2.setSpeed(lm2MaxSpeed * 70 / 100);
        lm2.backward();
        for (int i = 0; i < 10; i++) {
            scanRGB();
        }
        lm2.stop();
        lm2.setSpeed(lm2MaxSpeed * 75 / 100);
        lm2.rotateTo(turntablePosition);
    }

    private static void resetDevices() {
        // red flash
        led.setPattern(5);
        resetScan();
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

    private static void scanPiece(int f, int p, int[][] rgbs) {
        int idx = f * 9 + p;
        Delay.msDelay(100);
        scanRGB();
        rgbs[0][idx] = rgb[0];
        rgbs[1][idx] = rgb[1];
        rgbs[2][idx] = rgb[2];
    }

    private static void spin45() {
        turntablePosition = turntablePosition - (45 * 3);
        lm2.setSpeed(lm2MaxSpeed * 70 / 100);
        lm2.rotateTo(turntablePosition);
    }

    private static void scanMiddle(int f, int[][] rgbs) {
        Delay.msDelay(100);
        mm.setSpeed(mmMaxSpeed * 80 / 100);
        mm.rotateTo(-720);
        scanPiece(f, 0, rgbs);
    }

    private static void scanCorner(int f, int p, int[][] rgbs) {
        spin45();
        mm.setSpeed(mmMaxSpeed * 80 / 100);
        mm.rotateTo(-580);
        scanPiece(f, p, rgbs);
    }

    private static void scanEdge(int f, int p, int[][] rgbs) {
        spin45();
        mm.setSpeed(mmMaxSpeed * 80 / 100);
        mm.rotateTo(-620);
        scanPiece(f, p, rgbs);
    }

    private static void scanFace(int f, int[][] rgbs) {
        tiltAway();
        scanMiddle(f, rgbs);
        for (int i = 0; i < 4; i++) {
            int p = (i * 2 + 1) % 8;
            scanCorner(f, p, rgbs);
            p++;
            scanEdge(f, p, rgbs);
        }
        int pos = turntablePosition - lm2.getTachoCount();
        if (pos > 0) {
            turntablePosition = turntablePosition - (pos - pos % (360 * 3));
        }
        lm2.setSpeed(lm2MaxSpeed * 75 / 100);
        lm2.rotateTo(turntablePosition);
    }

    private static byte getColor(int r, int g, int b) {
        if (b < r && b < g && r > 100 && g > 100) {
            return ('Y');
        } else if (r + g + b > 400) {
            return ('W');
        } else if (r + g < b) {
            return ('B');
        } else if (g > b && g > r) {
            return ('G');
        } else if (b < 74 && g < 74 && g + b < r) {
            return ('R');
        } else {
            return ('O');
        }
    }

    private static boolean calculateColor(int[][] rgbs, byte[] color) {
        int i = 0;
        int count_y = 0, count_w = 0, count_b = 0, count_g = 0, count_r = 0, color_sum = 0;
        for (i = 0; i < 54; i++) {
            color[i] = getColor(rgbs[0][i], rgbs[1][i], rgbs[2][i]);
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
            writeLog("" + rgbs[0][i] + "," + rgbs[1][i] + "," + rgbs[2][i] + "," + (char) color[i]);
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
        int[][] rgbs = new int[3][54];
        drawMessage("Scan the cube");
        scanFace(0, rgbs);
        scanAway();
        tilt(1);
        scanFace(1, rgbs);
        scanAway();
        tilt(1);
        scanFace(2, rgbs);
        scanAway();
        spin(1);
        Delay.msDelay(100);
        tilt(1);
        scanFace(3, rgbs);
        scanAway();
        spin(-1);
        Delay.msDelay(100);
        tilt(1);
        scanFace(4, rgbs);
        scanAway();
        tilt(1);
        scanFace(5, rgbs);
        scanAway();
        return calculateColor(rgbs, color);
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
        scanAway();
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

    public static void main(String[] args) {
        try {
            boolean done = true;
            logFilePath = CubeSolver.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            int index = logFilePath.lastIndexOf(".");
            logFilePath = logFilePath.substring(0, index + 1);
            logFilePath += "log";
            removeLog();
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
