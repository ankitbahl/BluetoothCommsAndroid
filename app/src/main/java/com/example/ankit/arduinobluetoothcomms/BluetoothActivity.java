package com.example.ankit.arduinobluetoothcomms;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class BluetoothActivity extends Activity {
    static BluetoothAdapter bluetoothAdapter;
    static BluetoothDevice device;
    ConcurrentLinkedQueue<String> msgQueue = new ConcurrentLinkedQueue<>();
    Set<Byte> sentSignals = new HashSet<>();
    byte sentSignalCounter = 0;
    static boolean keepRunning = true;
    static long signalTimeoutMS = 2000;
    static String TAG = "Bluetooth";
    static AtomicInteger retryCounter;
    static UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        println("restarting");
        retryCounter = new AtomicInteger(0);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);
        setupBluetooth();
        buttonOnClicks();
    }

    private void buttonOnClicks() {
        int[] buttonIds = new int[]{
                R.id.off_button,
                R.id.on_button,
                R.id.restart_button
        };
        for(int buttonId : buttonIds) {
            (findViewById(buttonId)).setOnClickListener(view -> {
                switch (view.getId()) {
                    case R.id.off_button:
                        msgQueue.add("0");
                        break;
                    case R.id.on_button:
                        msgQueue.add("1");
                        break;
                    case R.id.restart_button:
                        setResult(RESULT_CANCELED);
                        finish();
                        break;
                }
            });
        }

        int[] pianoButtonIds = new int[] {
                R.id.c_button,
                R.id.d_button,
                R.id.e_button,
                R.id.f_button,
                R.id.g_button,
                R.id.a_button,
                R.id.b_button,
                R.id.c2_button
        };
        for(int pianoButtonId : pianoButtonIds) {
            (findViewById(pianoButtonId)).setOnTouchListener((v, event) -> {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    switch (v.getId()) {
                        case R.id.c_button:
                            msgQueue.add("2");
                            break;
                        case R.id.d_button:
                            msgQueue.add("3");
                            break;
                        case R.id.e_button:
                            msgQueue.add("4");
                            break;
                        case R.id.f_button:
                            msgQueue.add("5");
                            break;
                        case R.id.g_button:
                            msgQueue.add("6");
                            break;
                        case R.id.a_button:
                            msgQueue.add("7");
                            break;
                        case R.id.b_button:
                            msgQueue.add("8");
                            break;
                        case R.id.c2_button:
                            msgQueue.add("9");
                            break;

                    }
                    return true;
                } else if(event.getAction() == MotionEvent.ACTION_UP) {
                    msgQueue.add("0");
                    return true;
                }
                return false;
            });
        }
    }

    //TODO is there a better way
    private byte[] addHeaderToBytes(byte header, byte[] array) {
        byte[] newList = new byte[array.length + 1];
        newList[array.length] = header;
        for (int i = 0; i < array.length; i++) {
            newList[i] = array[i];
        }
        return newList;
    }

    private void receiveAck(int ack) {
        sentSignals.remove((byte)ack);
    }

    private void setupBluetooth() {
        println("Setting up");
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(bluetoothAdapter == null) {
            throw new RuntimeException("no bluetooth?");
        }
        if(!bluetoothAdapter.isEnabled()) {
            Intent enableBt = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBt, 1);
        }
        Set<BluetoothDevice> paired = bluetoothAdapter.getBondedDevices();

        for(BluetoothDevice pairedDevice : paired) {
            if(pairedDevice.getName().equals("HC-06")) {
                device = pairedDevice;
            }
        }

        ConnectThread thread = new ConnectThread();
        thread.start();
    }

    public void sendAndReadBytes(BluetoothSocket socket) {
        try(
                OutputStream outputStream = socket.getOutputStream();
                InputStream inputStream = socket.getInputStream()) {

            while (keepRunning) {
                long timer = Long.MAX_VALUE;
                if(msgQueue.size() > 0 && sentSignals.isEmpty()) {
                    byte[] sendBytes = msgQueue.poll().getBytes();
                    byte[] bytesWithHeader = addHeaderToBytes(sentSignalCounter, sendBytes);
                    sentSignals.add(sentSignalCounter);
                    sentSignalCounter++;
                    outputStream.write(bytesWithHeader);
                    timer = SystemClock.currentThreadTimeMillis();
                }

                while(inputStream.available() > 0) {
                    receiveAck(inputStream.read());
                    timer = Long.MAX_VALUE;
                }

                if(SystemClock.currentThreadTimeMillis() - timer > signalTimeoutMS) {
                    Log.e("uh oh", "Bluetooth device not working");
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error occurred, output stream closed", e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

    }

    private void println(Object o) {
        Log.d("Print Statement", o.toString());
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;

        ConnectThread() {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e("uh oh", "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter.cancelDiscovery();
            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                Log.e("uh oh", "Could not connect, retrying in 1s");
                connectException.printStackTrace();
                keepRunning = false;
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    closeException.printStackTrace();
                }
                //start a new thread to restart socket
                int retryCount = retryCounter.incrementAndGet();
                if(retryCount > 5) {
                    return;
                }
                ConnectThread thread = new ConnectThread();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                thread.start();
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            while(keepRunning) {
                sendAndReadBytes(mmSocket);
            }
            cancel();
        }

        // Closes the client socket and causes the thread to finish.
        void cancel() {
            try {
                println("closed");
                mmSocket.close();
            } catch (IOException e) {
                Log.e("Uh oh", "Could not close the client socket", e);
            }
        }
    }
}
