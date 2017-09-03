package com.example.ankit.arduinobluetoothcomms;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BluetoothActivity extends Activity {
    static BluetoothAdapter bluetoothAdapter;
    static BluetoothDevice device;
    ConcurrentLinkedQueue<String> msgQueue = new ConcurrentLinkedQueue<>();
    static boolean keepRunning = true;
    static String TAG = "Bluetooth";
    static UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);
        setupBluetooth();
        buttonOnClicks();
    }

    private void buttonOnClicks() {
        List<Button> buttons = new ArrayList<>();
        buttons.add((Button)findViewById(R.id.on_button));
        buttons.add((Button)findViewById(R.id.off_button));
        for(Button button : buttons) {
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    switch (view.getId()) {
                        case R.id.off_button:
                            msgQueue.add("0");
                            break;
                        case R.id.on_button:
                            msgQueue.add("1");
                            break;
                    }
                }
            });
        }
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

        ConnectThread thread = new ConnectThread(device);
        thread.start();
    }

    public void sendAndReadBytes(BluetoothSocket socket) {
        try(
                OutputStream outputStream = socket.getOutputStream();
                InputStream inputStream = socket.getInputStream()) {

            while (keepRunning) {
                if(msgQueue.size() > 0) {
                    outputStream.write(msgQueue.poll().getBytes());
                }

                while(inputStream.available() > 0) {
                    println(inputStream.read());
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
        private final BluetoothDevice mmDevice;

        ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            mmDevice = device;

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
                Log.e("uh oh", "Could not connect");
                connectException.printStackTrace();
                keepRunning = false;
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    closeException.printStackTrace();
                }
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
            //start a new thread to restart socket
            ConnectThread thread = new ConnectThread(device);
            thread.start();
        }
    }
}
