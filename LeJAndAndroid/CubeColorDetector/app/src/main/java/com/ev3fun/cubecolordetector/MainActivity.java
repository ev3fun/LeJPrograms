package com.ev3fun.cubecolordetector;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.IOException;
import java.lang.ref.WeakReference;

public class MainActivity extends Activity {

    private SurfaceDraw surfaceDraw = null;
    private Preview preview = null;
    private BTClient btClient = null;
    private boolean btStarted = false;

    private static class MyHandler extends Handler {

        private final WeakReference<MainActivity> mActivity;

        public MyHandler(MainActivity activity) {
            mActivity = new WeakReference<MainActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = mActivity.get();
            if (activity != null) {
                Context context = activity.getApplicationContext();
                switch (msg.what) {
                    case 1:
                        Toast.makeText(context, "Bluetooth connected successfully.", Toast.LENGTH_LONG).show();
                        break;
                    case 2:
                        // do the real work
                        activity.detectColors();
                        break;
                    case 3:
                        IOException e = (IOException) msg.obj;
                        Toast.makeText(context, "Bluetooth exception: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        break;
                }
            }
        }
    }

    private final MyHandler handler = new MyHandler(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FrameLayout frameLayout = (FrameLayout) findViewById(R.id.surface_view_holder);
        btClient = new BTClient(handler, "00:16:53:4D:38:80");
        preview = new Preview(this, btClient);
        surfaceDraw = new SurfaceDraw(this);
        frameLayout.addView(preview);
        frameLayout.addView(surfaceDraw);
        surfaceDraw.setPreview(preview);
    }

    @Override
    protected void onResume() {
        super.onResume();
        preview.initCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
        preview.releaseCamera();
        if (btStarted) {
            btClient.cancel();
        }
    }

    public void upButton(View view) {
        surfaceDraw.upMove();
    }

    public void downButton(View view) {
        surfaceDraw.downMove();
    }

    public void leftButton(View view) {
        surfaceDraw.leftMove();
    }

    public void rightButton(View view) {
        surfaceDraw.rightMove();
    }

    public void settingButton(View view) {
        if (!btStarted) {
            btClient.start();
            btStarted = true;
        }
    }

    public void detectColors() {
        surfaceDraw.detectColors(0);
    }

}
