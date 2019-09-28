package com.example.smarthome;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.Checkable;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.bluetooth.*;
import android.content.Intent;
import android.widget.ToggleButton;

public class MainActivity extends Activity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final String MAC_ADDRESS = "20:16:06:12:01:81"; //МАС-address Bluetooth
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //UUID
    private static final String LOG_TAG = "Borlehandro";
    private static final int ARDUINO_DATA = 1;

    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private BluetoothThred bluetoothThred = null;

    TextView arduinoDataText;
    Switch climateControl, autowateringControl, lightControl;
    ToggleButton conditionerButton, pumpButton, lightButton, thiefAlarmButton, fireAlarmButton;
    Handler bluetoothHandler;

    Map<String, Checkable> turnOnCodes = new HashMap<>();
    Map<String, Checkable> turnOffCodes = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        arduinoDataText = findViewById(R.id.dataTextView);

        if (btAdapter != null) {
            if (btAdapter.isEnabled()) {
                arduinoDataText.setText("Bluetooth ON");
            } else {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }

        } else printError("Can't find Bluetooth");


        climateControl = findViewById(R.id.climate_control);
        autowateringControl = findViewById(R.id.autowatering_control);
        lightControl = findViewById(R.id.light_control);
        thiefAlarmButton = findViewById(R.id.thief_alarm_button);
        fireAlarmButton = findViewById(R.id.fire_alarm_button);
        conditionerButton = findViewById(R.id.conditioner_button);
        pumpButton = findViewById(R.id.pump_button);
        lightButton = findViewById(R.id.light_button);

        turnOnCodes.put("1", climateControl);
        turnOnCodes.put("2", conditionerButton);
        turnOnCodes.put("4", autowateringControl);
        turnOnCodes.put("6", pumpButton);
        turnOnCodes.put("8", lightControl);
        turnOnCodes.put("A", thiefAlarmButton);
        turnOnCodes.put("F", fireAlarmButton);
        turnOnCodes.put("L", lightButton);

        turnOffCodes.put("0", climateControl);
        turnOffCodes.put("3", conditionerButton);
        turnOffCodes.put("5", autowateringControl);
        turnOffCodes.put("7", pumpButton);
        turnOffCodes.put("9", lightControl);
        turnOffCodes.put("a", thiefAlarmButton);
        turnOffCodes.put("f", fireAlarmButton);
        turnOffCodes.put("l", lightButton);


        bluetoothHandler = new Handler() {
            public void handleMessage(android.os.Message msg) {

                if (msg.what == ARDUINO_DATA) {
                    byte[] readBuf = (byte[]) msg.obj;
                    String strIncom = new String(readBuf, 0, msg.arg1);

                    if (turnOnCodes.get(strIncom) != null) {
                        turnOnCodes.get(strIncom).setChecked(true);
                    } else if (turnOffCodes.get(strIncom) != null) {
                        turnOffCodes.get(strIncom).setChecked(false);
                    }

                    correctVisiblyty();
                }
            }
        };
    }

    public void Clicked(View v) {

        if (!Checkable.class.isAssignableFrom(v.getClass())) {
            //HERE MAY BE SOMETHING ELSE IF WE WILL USE NO CHECKABLE VIEWS
            return;
        }

        Checkable c = (Checkable) v;

        if (c.isChecked())
            bluetoothThred.sendData(findInMap(turnOnCodes, c));
        else
            bluetoothThred.sendData(findInMap(turnOffCodes, c));
        correctVisiblyty();
    }

    @Override
    public void onResume() {
        super.onResume();

        BluetoothDevice device = btAdapter.getRemoteDevice(MAC_ADDRESS);
        Log.d(LOG_TAG, "Get Device " + device.getName());

        try {
            btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            Log.d(LOG_TAG, "Create Socket");

        } catch (IOException e) {
            printError("Can't create socket from onResume()" + e.getMessage() + ".");
        }

        btAdapter.cancelDiscovery();

        Log.d(LOG_TAG, "Chancel search of other devices");

        Log.d(LOG_TAG, "Try to connect...");

        try {

            btSocket.connect();
            Log.d(LOG_TAG, "Connection successful");

        } catch (IOException e) {

            try {

                btSocket.close();
                Log.d(LOG_TAG, "Can't connect");

            } catch (IOException e2) {

                printError("Can't close socket from onResume()" + e.getMessage() + ".");

            }
        }

        bluetoothThred = new BluetoothThred(btSocket);
        bluetoothThred.start();
    }

    @Override
    public void onPause() {
        super.onPause();

        Log.d(LOG_TAG, "onPause()");

        if (bluetoothThred.status_OutStrem() != null) bluetoothThred.cancel();

        try {
            btSocket.close();
        } catch (IOException e) {
            printError("Can't close socket from onPause()" + e.getMessage() + ".");
        }
    }


    private class BluetoothThred extends Thread {

        private final BluetoothSocket copyBtSocket;
        private final OutputStream outStrem;
        private final InputStream inStrem;

        BluetoothThred(BluetoothSocket socket) {

            copyBtSocket = socket;
            OutputStream tmpOut = null;
            InputStream tmpIn = null;

            try {
                tmpOut = socket.getOutputStream();
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Toast.makeText(MainActivity.this, "Connection error", Toast.LENGTH_SHORT).show();
            }

            outStrem = tmpOut;
            inStrem = tmpIn;
        }

        public void run() {

            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {

                try {
                    bytes = inStrem.read(buffer);
                    bluetoothHandler.obtainMessage(ARDUINO_DATA, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }

        void sendData(String message) {

            byte[] msgBuffer = message.getBytes();
            Log.d(LOG_TAG, "Sending data: " + message);

            try {

                outStrem.write(msgBuffer);

            } catch (IOException e) {
                Toast.makeText(MainActivity.this, "Can't send data", Toast.LENGTH_SHORT).show();
            }
        }

        void cancel() {
            try {
                copyBtSocket.close();
            } catch (IOException e) {
                Log.w(LOG_TAG, "Can't close socket: "+ e.getMessage());
            }
        }

        Object status_OutStrem() {
            return outStrem;
        }
    }

    String findInMap(Map<String, Checkable> map, Checkable item) {

        for (Map.Entry<String, Checkable> elem : map.entrySet()) {

            if (elem.getValue() == item) return elem.getKey();

        }

        return null;
    }

    void correctVisiblyty() {
        if (climateControl.isChecked())
            conditionerButton.setVisibility(View.INVISIBLE);
        else
            conditionerButton.setVisibility(View.VISIBLE);

        if (autowateringControl.isChecked())
            pumpButton.setVisibility(View.INVISIBLE);
        else
            pumpButton.setVisibility(View.VISIBLE);

        if (lightControl.isChecked())
            lightButton.setVisibility(View.INVISIBLE);
        else
            lightButton.setVisibility(View.VISIBLE);
    }

    void printError(String message) {
        Log.e(LOG_TAG, message);
        finish();
    }
}
