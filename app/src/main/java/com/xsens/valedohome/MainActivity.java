package com.xsens.valedohome;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;


public class MainActivity extends Activity implements BluetoothAdapter.LeScanCallback {
    private static final String TAG = "BluetoothGattActivity";

    private static final String DEVICE_NAME = "ValedoHome";

    /* Device Configuration */
    private static final UUID DEVICE_CONFIGURATION         = UUID.fromString("14a1893f-9b14-4b60-a530-97a302993374");
    private static final UUID POWER_CONTROL                = UUID.fromString("97f6bc36-00f3-4730-aab8-bb33836c14aa");
    private static final UUID LED_STATE                    = UUID.fromString("ee5159fe-4a05-4aab-833d-9f88b5a42b48");
    private static final UUID LOCAL_NAME                   = UUID.fromString("69a53fba-f69c-454b-9b3b-fd8d33128dad");
    private static final UUID SELF_TEST_REPORT             = UUID.fromString("5e8236b8-b94d-4a65-8b9f-2ddaa6d69bb7");
    private static final UUID HEADING_REDEFINITION_CONTROL = UUID.fromString("4ec79fb8-88bf-4392-84ec-a0475823f8fb");

    /* Awinda Service */
    private static final UUID AWINDA_SERVICE               = UUID.fromString("e8a68b2a-b616-45c0-b8d0-d9ddf447731e");
    private static final UUID ORIENTATION                  = UUID.fromString("cf54bf43-3d66-4666-8fd3-7df5788b73c1");
    private static final UUID HIGH_PASS_VELOCITY           = UUID.fromString("1a431fae-e870-485a-aba3-89b3b525c570");

    /* Client Configuration Descriptor */
    private static final UUID CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");


    private BluetoothAdapter mBluetoothAdapter;
    private SparseArray<BluetoothDevice> mDevices;

    private BluetoothGatt mConnectedGatt;

    private TextView mQuatX, mQuatY, mQuatZ, mQuatW;

    private ProgressDialog mProgress;

    private QuaternionGraph quaternionGraph;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_main);
        setProgressBarIndeterminate(true);

        /*
         * We're goint to create a quaternion graph, showing four values on a single layout
         */
        quaternionGraph = new QuaternionGraph();
        quaternionGraph.showGraph(this, (LinearLayout) findViewById(R.id.graph));


        /*
         * We are going to display the results in some text fields
         */
//        mQuatW = (TextView) findViewById(R.id.text_quat_w);
//        mQuatX = (TextView) findViewById(R.id.text_quat_x);
//        mQuatY = (TextView) findViewById(R.id.text_quat_y);
//        mQuatZ = (TextView) findViewById(R.id.text_quat_z);

        /*
         * Bluetooth in Android 4.3 is accessed via the BluetoothManager, rather than
         * the old static BluetoothAdapter.getInstance()
         */
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();

        mDevices = new SparseArray<BluetoothDevice>();

        /*
         * A progress dialog will be needed while the connection process is
         * taking place
         */
        mProgress = new ProgressDialog(this);
        mProgress.setIndeterminate(true);
        mProgress.setCancelable(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        /*
         * We need to enforce that Bluetooth is first enabled, and take the
         * user to settings to enable it if they have not done so.
         */
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            //Bluetooth is disabled
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
            finish();
            return;
        }

        /*
         * Check for Bluetooth LE Support.  In production, our manifest entry will keep this
         * from installing on these devices, but this will allow test devices or other
         * sideloads to report whether or not the feature exists.
         */
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "No LE Support.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        clearDisplayValues();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //Make sure dialog is hidden
        mProgress.dismiss();
        //Cancel any scans in progress
        mHandler.removeCallbacks(mStopRunnable);
        mHandler.removeCallbacks(mStartRunnable);
        mBluetoothAdapter.stopLeScan(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        //Disconnect from any active tag connection
        if (mConnectedGatt != null) {
            Log.w(TAG, "Stoping activity");
            mConnectedGatt.disconnect();
            mConnectedGatt = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Add the "scan" option to the menu
        getMenuInflater().inflate(R.menu.main, menu);
        //Add any device elements we've discovered to the overflow menu
        for (int i=0; i < mDevices.size(); i++) {
            BluetoothDevice device = mDevices.valueAt(i);
            menu.add(0, mDevices.keyAt(i), 0, device.getName());
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_scan:
                mDevices.clear();
                startScan();
                return true;
            default:
                //Obtain the discovered device to connect with
                BluetoothDevice device = mDevices.get(item.getItemId());
                Log.i(TAG, "Connecting to "+device.getName());
                /*
                 * Make a connection with the device using the special LE-specific
                 * connectGatt() method, passing in a callback for GATT events
                 */
                mConnectedGatt = device.connectGatt(this, false, mGattCallback);
                //Display progress UI
                mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Connecting to "+device.getName()+"..."));
                Toast.makeText(this, "Connecting to "+device.getName()+"...", Toast.LENGTH_LONG);

                return super.onOptionsItemSelected(item);
        }
    }

    private void clearDisplayValues() {
//        mQuatW.setText("---");
//        mQuatX.setText("---");
//        mQuatY.setText("---");
//        mQuatZ.setText("---");
    }


    private Runnable mStopRunnable = new Runnable() {
        @Override
        public void run() {
            stopScan();
        }
    };
    private Runnable mStartRunnable = new Runnable() {
        @Override
        public void run() {
            startScan();
        }
    };

    private void startScan() {
        mBluetoothAdapter.startLeScan(this);
        setProgressBarIndeterminateVisibility(true);

        mHandler.postDelayed(mStopRunnable, 2500);
    }

    private void stopScan() {
        mBluetoothAdapter.stopLeScan(this);
        setProgressBarIndeterminateVisibility(false);
    }

    /* BluetoothAdapter.LeScanCallback */

    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        Log.i(TAG, "New LE Device: " + device.getName() + " @ " + rssi);
        /*
         * We are looking for SensorTag devices only, so validate the name
         * that each device reports before adding it to our collection
         */
        if (DEVICE_NAME.equals(device.getName())) {
            mDevices.put(device.hashCode(), device);
            //Update the overflow menu
            invalidateOptionsMenu();
        }
    }

    /*
     * In this callback, we've created a bit of a state machine to enforce that only
     * one characteristic be read or written at a time until all of our sensors
     * are enabled and we are registered to get notifications.
     */
    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        /* State Machine Tracking */
        private int mState = 0;

        private void reset() { mState = 0; }

        private void advance() { mState++; }

        /*
         * Read the data characteristic's value for each sensor explicitly
         */
        private void readNextSensor(BluetoothGatt gatt) {
            BluetoothGattCharacteristic characteristic;
            Log.i(TAG, "readNextSensor: "+ mState);
            switch (mState) {
                case 0:
                    Log.d(TAG, "Reading orientation");
                    characteristic = gatt.getService(AWINDA_SERVICE)
                            .getCharacteristic(ORIENTATION);
                    break;
                case 1:
                    Log.d(TAG, "Reading velocity");
                    characteristic = gatt.getService(AWINDA_SERVICE)
                            .getCharacteristic(HIGH_PASS_VELOCITY);
                    break;
                default:
                    mHandler.sendEmptyMessage(MSG_DISMISS);
                    Log.i(TAG, "All Sensors Enabled");
                    return;
            }

            gatt.readCharacteristic(characteristic);
        }

        /*
         * Enable notification of changes on the data characteristic for each sensor
         * by writing the ENABLE_NOTIFICATION_VALUE flag to that characteristic's
         * configuration descriptor.
         */
        private void setNotifyNextSensor(BluetoothGatt gatt) {
            Log.i(TAG, "setNotifyNextSensor, mState = " + mState);
            BluetoothGattCharacteristic characteristic;
            switch (mState) {
                case 0:
                    Log.d(TAG, "Set notify orientation");
                    characteristic = gatt.getService(AWINDA_SERVICE)
                            .getCharacteristic(ORIENTATION);
                    break;
                case 1:
                    Log.d(TAG, "Set notify velocity");
                    characteristic = gatt.getService(AWINDA_SERVICE)
                            .getCharacteristic(HIGH_PASS_VELOCITY);
                    break;
                default:
                    mHandler.sendEmptyMessage(MSG_DISMISS);
                    Log.i(TAG, "All Sensors Enabled");
                    return;
            }

            //Enable local notifications
            gatt.setCharacteristicNotification(characteristic, true);
            //Enabled remote notifications
            BluetoothGattDescriptor desc = characteristic.getDescriptor(CONFIG_DESCRIPTOR);
            desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(desc);
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i(TAG, "Connection State Change: "+status+" -> "+connectionState(newState));
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                /*
                 * Once successfully connected, we must next discover all the services on the
                 * device before we can read and write their characteristics.
                 */
                gatt.discoverServices();
                mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Discovering Services..."));
            } else if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
                /*
                 * If at any point we disconnect, send a message to clear the weather values
                 * out of the UI
                 */
                mHandler.sendEmptyMessage(MSG_CLEAR);
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                /*
                 * If there is a failure at any stage, simply disconnect
                 */
                Log.w(TAG, "onConnectionStateChange: status != GATT_SUCCESS");
                gatt.disconnect();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.i(TAG, "Success: Services Discovered: "+status);

            mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Reading Sensors..."));
            /*
             * With services discovered, we are going to reset our state machine and start
             * working through the sensors we need to read
             */
            reset();
            //readNextSensor(gatt); //option 1
            setNotifyNextSensor(gatt); //option 2
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //For each read, pass the data up to the UI thread to update the display
            Log.i(TAG, "onCharacteristicRead");
            if (ORIENTATION.equals(characteristic.getUuid())) {
                Log.d(TAG, "Sending orientation to handler");
                mHandler.sendMessage(Message.obtain(null, MSG_ORIENTATION, characteristic));
            }
            if (HIGH_PASS_VELOCITY.equals(characteristic.getUuid())) {
                Log.d(TAG, "Sending high pass velocity to handler");
                mHandler.sendMessage(Message.obtain(null, MSG_HIGH_PASS_VELOCITY, characteristic));
            }

            //After reading the initial value, next we enable notifications
            setNotifyNextSensor(gatt);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //After writing the enable flag, next we read the initial value
            Log.i(TAG, "onCharacteristicWrite");
            readNextSensor(gatt);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            /*
             * After notifications are enabled, all updates from the device on characteristic
             * value changes will be posted here.  Similar to read, we hand these up to the
             * UI thread to update the display.
             */

            Log.i(TAG, "onCharacteristicChanged");
            if (ORIENTATION.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_ORIENTATION, characteristic));
            }
            if (HIGH_PASS_VELOCITY.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_HIGH_PASS_VELOCITY, characteristic));
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.i(TAG, "onDescriptorWrite");
            //Once notifications are enabled, we move to the next sensor and start over with enable
            advance();
            //readNextSensor(gatt); //option 1
            setNotifyNextSensor(gatt); //option 2
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log.d(TAG, "Remote RSSI: "+rssi);
        }

        private String connectionState(int status) {
            switch (status) {
                case BluetoothProfile.STATE_CONNECTED:
                    return "Connected";
                case BluetoothProfile.STATE_DISCONNECTED:
                    return "Disconnected";
                case BluetoothProfile.STATE_CONNECTING:
                    return "Connecting";
                case BluetoothProfile.STATE_DISCONNECTING:
                    return "Disconnecting";
                default:
                    return String.valueOf(status);
            }
        }
    };

    /*
     * We have a Handler to process event results on the main thread
     */

    private static final int MSG_ORIENTATION = 101;
    private static final int MSG_HIGH_PASS_VELOCITY = 102;
    private static final int MSG_PROGRESS = 201;
    private static final int MSG_DISMISS = 202;
    private static final int MSG_CLEAR = 301;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.i(TAG, "handleMessage: "+msg.toString());
            BluetoothGattCharacteristic characteristic;
            switch (msg.what) {
                case MSG_ORIENTATION:
                    characteristic = (BluetoothGattCharacteristic) msg.obj;
                    if (characteristic.getValue() == null) {
                        Log.w(TAG, "Error obtaining humidity value");
                        return;
                    }
                    updateOrientationValues(characteristic);
                    break;
                case MSG_PROGRESS:
                    mProgress.setMessage((String) msg.obj);
                    if (!mProgress.isShowing()) {
                        mProgress.show();
                    }
                    break;
                case MSG_DISMISS:
                    mProgress.hide();
                    break;
                case MSG_CLEAR:
                    clearDisplayValues();
                    break;
            }
        }
    };

    /* Methods to extract sensor data and update the UI */

    private void updateOrientationValues(BluetoothGattCharacteristic characteristic) {
//        long time = System.nanoTime();
        byte[] bytes = characteristic.getValue();

        float w = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
        float x = ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
        float y = ByteBuffer.wrap(bytes, 8, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
        float z = ByteBuffer.wrap(bytes, 12, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();

        //Add the new datapoint to the graph, and it will handle the presentation
        quaternionGraph.addDataPoint(w, x, y, z);

        //testing string parse time
//        Log.d("Float time", Long.toString(time - System.nanoTime()));

//        mQuatW.setText(Float.toString(w));
//        mQuatX.setText(Float.toString(x));
//        mQuatY.setText(Float.toString(y));
//        mQuatZ.setText(Float.toString(z));

        //trying to figure out if printing the values is causing the 0.5s lag.
//        Log.d("Parse time", Long.toString(time - System.nanoTime()));
    }
}
