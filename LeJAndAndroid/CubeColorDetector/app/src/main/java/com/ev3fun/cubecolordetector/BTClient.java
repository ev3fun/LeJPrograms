package com.ev3fun.cubecolordetector;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BTClient extends Thread {
    private static final String SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB";
    private BluetoothSocket bluetoothSocket = null;
    private OutputStream outputStream = null;
    private Handler handler = null;
    private String macAddress = null;
    private boolean endThread = false;

    BTClient(Handler hd, String mac) {
        handler = hd;
        macAddress = mac;
    }

    public void run() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(macAddress);
        try {
            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(UUID
                    .fromString(SPP_UUID));
            bluetoothSocket.connect();
            InputStream inputStream = bluetoothSocket.getInputStream();
            outputStream = bluetoothSocket.getOutputStream();
            // send message for connect successful.
            handler.obtainMessage(1).sendToTarget();

            byte[] readBuf = new byte[8];
            while (!endThread) {
                int num = inputStream.read(readBuf);
                if (num == 1 && readBuf[0] == 'c') {
                    handler.obtainMessage(2).sendToTarget();
                }
            }
        } catch (IOException e) {
            handler.obtainMessage(3, e).sendToTarget();
            e.printStackTrace();
        }
    }

    public void write(byte[] bytes) {
        try {
            outputStream.write(bytes);
            outputStream.flush();
        } catch (IOException e) {
            handler.obtainMessage(3, e).sendToTarget();
            e.printStackTrace();
        }
    }

    public void cancel() {
        try {
            endThread = true;
            bluetoothSocket.close();
        } catch (IOException e) {
            handler.obtainMessage(3, e).sendToTarget();
            e.printStackTrace();
        }
    }

}
