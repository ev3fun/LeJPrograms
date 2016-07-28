package com.ev3fun.cubecolordetector;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

class Preview extends SurfaceView implements SurfaceHolder.Callback,
        Camera.PreviewCallback, Camera.AutoFocusCallback {

    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private BTClient btClient = null;
    // protect the following 5 variables
    private ReentrantLock dataLock = new ReentrantLock();
    private float[] ox = new float[9];
    private float[] oy = new float[9];
    private byte[] color = new byte[10];
    private int[][] rgbColor = new int[9][3];
    private boolean ready;
    private boolean hasSent;

    Preview(Context context, BTClient client) {
        super(context);
        ready = false;
        hasSent = false;
        surfaceHolder = getHolder();
        surfaceHolder.addCallback(this);
        btClient = client;
    }

    public void initCamera() {
        camera = Camera.open();
    }

    public void releaseCamera() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    public void detectColors(int index, float[] x, float[] y) {
        if (camera != null) {
            dataLock.lock();
            try {
                ready = false;
                hasSent = false;
                color[9] = (byte) index;
                System.arraycopy(x, 0, ox, 0, 9);
                System.arraycopy(y, 0, oy, 0, 9);
            } finally {
                dataLock.unlock();
            }
            camera.autoFocus(this);
        }
    }

    public boolean getColors(byte[] color, int[][] rgb) {
        boolean ret = false;
        dataLock.lock();
        try {
            if (ready) {
                System.arraycopy(this.color, 0, color, 0, 10);
                for (int i = 0; i < 9; i++) {
                    for (int j = 0; j < 3; j++) {
                        rgb[i][j] = rgbColor[i][j];
                    }
                }
                ret = true;
                if (!hasSent) {
                    btClient.write(color);
                    hasSent = true;
                }
            }
        } finally {
            dataLock.unlock();
        }
        return ret;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            if (camera != null) {
                camera.setPreviewDisplay(holder);
            }
        } catch (IOException exception) {
            Log.e("Error", "IOException caused by setPreviewDisplay()", exception);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        releaseCamera();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (camera == null) {
            return;
        }
        Camera.Parameters parameters = camera.getParameters();
        parameters.setPreviewSize(height, width);
        parameters.setPreviewFormat(ImageFormat.NV21);
        // set focus area
        List<Camera.Area> focusArea = new ArrayList<Camera.Area>();
        focusArea.add(new Camera.Area(new Rect(-100, -600, 100, 400), 1000));
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        parameters.setFocusAreas(focusArea);
        // parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        camera.setDisplayOrientation(90);
        camera.setParameters(parameters);
        camera.startPreview();
    }

    @Override
    public void onAutoFocus(boolean success, Camera camera) {
        if (!success) {
            // just log a message
            Log.e("Error", "AutoFocus failed");
        }
        camera.setOneShotPreviewCallback(this);
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        int frameHeight = camera.getParameters().getPreviewSize().height;
        int frameWidth = camera.getParameters().getPreviewSize().width;
        int rgb[] = new int[3];
        int cx, cy;
        int diff1, diff2;
        for (int i = 0; i < 9; i++) {
            cx = (int) oy[i];
            cy = (int) (frameHeight - ox[i]);
            getRGBFromYUV420SP(data, frameWidth, frameHeight, cx, cy, rgb);
            rgbColor[i][0] = rgb[0];
            rgbColor[i][1] = rgb[1];
            rgbColor[i][2] = rgb[2];
            diff1 = Math.abs(rgb[0] - rgb[1]) * 100;
            diff2 = Math.abs(rgb[0] - rgb[2]) * 100;
            if (rgb[0] > 0) {
                diff1 = diff1 / rgb[0];
                diff2 = diff2 / rgb[0];
            }
            if (diff1 < 20 && diff2 < 20 && rgb[0] > 100 && rgb[1] > 100 && rgb[2] > 100) {
                color[i] = 'W';
            } else if (rgb[1] > rgb[2] && rgb[1] > rgb[0]) {
                color[i] = 'G';
            } else if (rgb[2] > rgb[1] && rgb[2] > rgb[0]) {
                color[i] = 'B';
            } else if (rgb[0] > rgb[2] && rgb[2] >= rgb[1]) {
                color[i] = 'R';
            } else if (rgb[0] > rgb[1] && rgb[1] > rgb[2]) {
                if (diff1 < 30) {
                    color[i] = 'Y';
                } else {
                    color[i] = 'O';
                }
            } else {
                color[i] = 'U';
            }
        }
        dataLock.lock();
        try {
            ready = true;
        } finally {
            dataLock.unlock();
        }
    }

    public static void getRGBFromYUV420SP(byte[] yuv420sp, int width, int height,
                                          int ox, int oy, int[] rgb) {
        final int frameSize = width * height;
        final int radius = 4;
        int rgbCount = 0;
        int ax = (ox - radius) & (~1);
        int ay = oy - radius;
        int bx = ox + radius - 1;
        int by = oy + radius - 1;
        rgb[0] = rgb[1] = rgb[2] = 0;
        for (int j = ay; j < by; j++) {
            int uvp = frameSize + (j >> 1) * width + ax, u = 0, v = 0;
            for (int i = ax; i < bx; i++, rgbCount++) {
                int yp = j * width + i;
                int y = (0xff & ((int) yuv420sp[yp])) - 16;
                if (y < 0)
                    y = 0;
                if ((i & 1) == 0) {
                    v = (0xff & yuv420sp[uvp++]) - 128;
                    u = (0xff & yuv420sp[uvp++]) - 128;
                }
                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);
                if (r < 0)
                    r = 0;
                else if (r > 262143)
                    r = 262143;
                if (g < 0)
                    g = 0;
                else if (g > 262143)
                    g = 262143;
                if (b < 0)
                    b = 0;
                else if (b > 262143)
                    b = 262143;
                rgb[0] += (r >> 10) & 0xff;
                rgb[1] += (g >> 10) & 0xff;
                rgb[2] += (b >> 10) & 0xff;
            }
        }
        rgb[0] = rgb[0] / rgbCount;
        rgb[1] = rgb[1] / rgbCount;
        rgb[2] = rgb[2] / rgbCount;
    }

}
