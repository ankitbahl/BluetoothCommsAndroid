package com.example.ankit.arduinobluetoothcomms;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Intent bluetoothIntent = new Intent(this, BluetoothActivity.class);
        startActivity(bluetoothIntent);
    }
}
