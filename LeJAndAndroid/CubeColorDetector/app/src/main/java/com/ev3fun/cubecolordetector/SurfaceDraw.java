package com.ev3fun.cubecolordetector;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class SurfaceDraw extends SurfaceView implements Runnable, SurfaceHolder.Callback {

    private Preview preview = null;
    private SurfaceHolder surfaceHolder;
    private Paint paint = new Paint();
    private Thread drawThread;
    private volatile boolean runFlag = false;
    private float pieceLen;
    private float smallPieceLen;
    private float[] ox = new float[9];
    private float[] oy = new float[9];
    private byte[] color = new byte[10];
    private int[][] rgb = new int[9][3];
    private float[] sox = new float[9];
    private float[] soy = new float[9];

    public float xCenter = 360;
    public float yCenter = 360;

    public SurfaceDraw(Context context) {
        super(context);
        surfaceHolder = getHolder();
        surfaceHolder.addCallback(this);
        paint.setAntiAlias(true);
        setZOrderOnTop(true);
        surfaceHolder.setFormat(PixelFormat.TRANSLUCENT);
        for (int i = 0; i < 9; i++) {
            color[i] = 'U';
        }
        color[9] = (byte) 7;
    }

    public void setPreview(Preview preview) {
        this.preview = preview;
    }

    public void detectColors(int index) {
        preview.detectColors(index, ox, oy);
    }

    public void leftMove() {
        xCenter = xCenter - 1;
        calculateCenters();
    }

    public void upMove() {
        yCenter = yCenter - 1;
        calculateCenters();
    }

    public void rightMove() {
        xCenter = xCenter + 1;
        calculateCenters();
    }

    public void downMove() {
        yCenter = yCenter + 1;
        calculateCenters();
    }

    private void draw(Canvas canvas, Paint paint) {
        int i, fillColor;
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        for (i = 0; i < 9; i++) {
            float left = ox[i] - 25;
            float top = oy[i] - 25;
            float right = ox[i] + 25 - 1;
            float bottom = oy[i] + 25 - 1;
            canvas.drawRect(left, top, right, bottom, paint);
        }
        paint.setColor(Color.BLACK);
        float radius = smallPieceLen * 0.5f;
        for (i = 0; i < 9; i++) {
            float left = sox[i] - radius;
            float top = soy[i] - radius;
            float right = sox[i] + radius - 1;
            float bottom = soy[i] + radius - 1;
            canvas.drawRect(left, top, right, bottom, paint);
            if (color[i] == 'U') {
                canvas.drawLine(left, top, right, bottom, paint);
            }
        }
        if (preview != null) {
            if (preview.getColors(color, rgb)) {
                for (i = 0; i < 9; i++) {
                    float left = sox[i] - radius + 1;
                    float top = soy[i] - radius + 1;
                    float right = sox[i] + radius - 2;
                    float bottom = soy[i] + radius - 2;
                    if (color[i] == 'W') {
                        fillColor = Color.WHITE;
                    } else if (color[i] == 'Y') {
                        fillColor = Color.YELLOW;
                    } else if (color[i] == 'B') {
                        fillColor = Color.BLUE;
                    } else if (color[i] == 'G') {
                        fillColor = Color.GREEN;
                    } else if (color[i] == 'R') {
                        fillColor = Color.RED;
                    } else if (color[i] == 'O') {
                        fillColor = Color.rgb(255, 69, 0);
                    } else {
                        fillColor = Color.TRANSPARENT;
                    }
                    paint.setStyle(Paint.Style.FILL);
                    if (fillColor == Color.TRANSPARENT) {
                        paint.setColor(Color.rgb(rgb[i][0], rgb[i][1], rgb[i][2]));
                        canvas.drawCircle(sox[i], soy[i], radius * 0.6f, paint);
                    } else {
                        paint.setColor(fillColor);
                        canvas.drawRect(left, top, right, bottom, paint);
                    }
                    paint.setColor(Color.WHITE);
                    paint.setTextSize(30);
                    canvas.drawText("R=" + rgb[i][0], left, top + (smallPieceLen * 0.3f), paint);
                    canvas.drawText("G=" + rgb[i][1], left, top + (smallPieceLen * 0.6f), paint);
                    canvas.drawText("B=" + rgb[i][2], left, top + smallPieceLen * 0.9f, paint);
                }
            }
        }
    }

    @Override
    public void run() {
        while (runFlag) {
            Canvas canvas = null;
            try {
                canvas = surfaceHolder.lockCanvas();
                if (canvas != null) {
                    draw(canvas, paint);
                }
            } catch (Exception e) {
                Log.e("Error", "Refresh screen error" + e);
            } finally {
                if (canvas != null) {
                    surfaceHolder.unlockCanvasAndPost(canvas);
                }
            }
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                Log.e("Error", "Sleep was interrupted" + e);
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        final float padFactor = 0.25f;
        int screenWidth = getWidth();
        int screenHeight = getHeight();
        pieceLen = screenWidth / (3 + padFactor * 2);
        smallPieceLen = screenWidth / 6;
        xCenter = screenWidth / 2;
        yCenter = screenWidth / 2 - pieceLen * padFactor / 2;
        calculateSmallCenters(screenHeight);
        calculateCenters();
        runFlag = true;
        drawThread = new Thread(this);
        drawThread.start();
    }

    private void calculateCenters() {
        ox[0] = xCenter;
        oy[0] = yCenter;
        ox[1] = xCenter - pieceLen;
        oy[1] = yCenter + pieceLen;
        ox[2] = ox[1];
        oy[2] = yCenter;
        ox[3] = ox[1];
        oy[3] = yCenter - pieceLen;
        ox[4] = xCenter;
        oy[4] = oy[3];
        ox[5] = xCenter + pieceLen;
        oy[5] = oy[3];
        ox[6] = ox[5];
        oy[6] = yCenter;
        ox[7] = ox[5];
        oy[7] = oy[1];
        ox[8] = xCenter;
        oy[8] = oy[1];
    }

    private void calculateSmallCenters(int screenHeight) {
        sox[0] = smallPieceLen * 2.5f;
        soy[0] = screenHeight * 0.5f + smallPieceLen * 2.5f;
        sox[1] = sox[0] - smallPieceLen;
        soy[1] = soy[0] + smallPieceLen;
        sox[2] = sox[1];
        soy[2] = soy[0];
        sox[3] = sox[1];
        soy[3] = soy[0] - smallPieceLen;
        sox[4] = sox[0];
        soy[4] = soy[3];
        sox[5] = sox[0] + smallPieceLen;
        soy[5] = soy[3];
        sox[6] = sox[5];
        soy[6] = soy[0];
        sox[7] = sox[5];
        soy[7] = soy[1];
        sox[8] = sox[0];
        soy[8] = soy[1];
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        boolean retry = true;
        runFlag = false;
        while (retry) {
            try {
                drawThread.join();
                retry = false;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

}
