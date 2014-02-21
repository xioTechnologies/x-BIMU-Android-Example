package rug.xio.xbimudemo;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements SerialMessageHandler, SerialPacketHandler {

	public static final String LOG_TAG = "xBIMUDemo";

	// Intent request codes
	private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;

	// Name of the connected device
	private String mConnectedDeviceName = null;

	private BluetoothAdapter mBluetoothAdapter = null;
	private static BluetoothSerialService mSerialService = null;

	private boolean mEnablingBT;
	private MenuItem mMenuItemConnect;
    
	private static TextView mTitle;	
	
	private static SerialDecoder mSerialDecoder;

	private static TextView mBatteryLevel;
	private static TextView mGyroscope;
	private static TextView mAccelerometer;
	private static TextView mMagnetometer;
	private static TextView mQuaternion;

	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		if (mBluetoothAdapter == null) {
			finishDialogNoBluetooth();
			return;
		}

        // Set up the window layout
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        
        setContentView(R.layout.activity_main);
        
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);
		
        // Set up the custom title
        mTitle = (TextView) findViewById(R.id.title_left_text);
        mTitle.setText(R.string.app_name);
        mTitle = (TextView) findViewById(R.id.title_right_text);
        
        // hook up UI elements
    	mBatteryLevel = (TextView) findViewById(R.id.BatteryLevel);
    	mGyroscope = (TextView) findViewById(R.id.Gyroscope);
    	mAccelerometer = (TextView) findViewById(R.id.Accelerometer);
    	mMagnetometer = (TextView) findViewById(R.id.Magnetometer);
    	mQuaternion = (TextView) findViewById(R.id.Quaternion);
    	    	
    	// set up the serial decoder
        mSerialDecoder = new SerialDecoder();         		
        
        mSerialDecoder.OKReceived = this;
        mSerialDecoder.ErrorReceived = this; 
        mSerialDecoder.QuaternionReceived = this; 
        mSerialDecoder.BatteryReceived = this;
        mSerialDecoder.SensorsReceived = this; 
        mSerialDecoder.ThermometerReceived = this;
        
        // create the serial service, assign the bluetooth state handler and the serial decoder as the SerialConsumer
		mSerialService = new BluetoothSerialService(this, mHandlerBT, mSerialDecoder);
	}
	

	
	
	// Send serial data to the port
	public void send(byte[] out) {
		mSerialService.write(out);
	}
	
	// The Handler that gets information back from the SerialDecoder
	private final Handler mHandlerSerial = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			int[] values; 
			
			switch (msg.what) {
			case SerialDecoder.MESSAGE_OK:	
				break;
			case SerialDecoder.MESSAGE_Error:				
				break;
			case SerialDecoder.PACKET_Battery:
				
				values = (int[])msg.obj;
				
				int battery = values[0];
				
				float percent = ((float)battery / 3800.0f) * 100.0f; // ? 
				
				mBatteryLevel.setText(percent + "%"); 

				break;
			case SerialDecoder.PACKET_Quaternion:

				values = (int[])msg.obj;
				
				mQuaternion.setText(values[0] + ", " + values[1] + ", " + values[2] + ", " + values[3]);

				break;
			case SerialDecoder.PACKET_Sensors:
				values = (int[])msg.obj;
				
				mGyroscope.setText(values[0] + ", " + values[1] + ", " + values[2]);
				mAccelerometer.setText(values[3] + ", " + values[4] + ", " + values[5]);
				mMagnetometer.setText(values[6] + ", " + values[7] + ", " + values[8]);

				break;
			case SerialDecoder.PACKET_Thermometer:
				
				break;			
			}
		}
	};

	
	@Override
	public void onSerialPacket(int type, int length, int[] args) {
		
		// Send the serial packet to the UI Thread
		Message msg = mHandlerSerial.obtainMessage(type);
		
		msg.arg1 = length; 
		
		msg.obj = args;

		mHandlerSerial.sendMessage(msg);		
	}


	@Override
	public void onSerialMessage(int type, int length) {
		
		// Send the serial message to the UI thread
		Message msg = mHandlerSerial.obtainMessage(type);
		
		msg.arg1 = length; 	

		mHandlerSerial.sendMessage(msg);
	}
	
	
	
	
	
	@Override
	public void onStart() {
		super.onStart();	
		
		mEnablingBT = false;
	}

	@Override
	public synchronized void onResume() {
		super.onResume();
		
		if (!mEnablingBT) { // If we are turning on the BT we cannot check if it's enable
		    if ( (mBluetoothAdapter != null)  && (!mBluetoothAdapter.isEnabled()) ) {
			
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.alert_dialog_turn_on_bt)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.alert_dialog_warning_title)
                    .setCancelable( false )
                    .setPositiveButton(R.string.alert_dialog_yes, new DialogInterface.OnClickListener() {
                    	public void onClick(DialogInterface dialog, int id) {
                    		mEnablingBT = true;
                    		Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    		startActivityForResult(enableIntent, REQUEST_ENABLE_BT);			
                    	}
                    })
                    .setNegativeButton(R.string.alert_dialog_no, new DialogInterface.OnClickListener() {
                    	public void onClick(DialogInterface dialog, int id) {
                    		finishDialogNoBluetooth();            	
                    	}
                    });
                AlertDialog alert = builder.create();
                alert.show();
		    }		
		
		    if (mSerialService != null) {
		    	// Only if the state is STATE_NONE, do we know that we haven't started already
		    	if (mSerialService.getState() == BluetoothSerialService.STATE_NONE) {
		    		// Start the Bluetooth service
		    		mSerialService.start();
		    	}
		    }
		}
	}

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

	@Override
	public synchronized void onPause() {
		super.onPause();		
	}

    @Override
    public void onStop() {
        super.onStop();        
    }


	@Override
	public void onDestroy() {
		super.onDestroy();
		
        if (mSerialService != null) { 
        	mSerialService.stop();
        }        
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();

        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.options_menu, menu);
		
        mMenuItemConnect = menu.getItem(0);
        
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.connect:

			if (getConnectionState() == BluetoothSerialService.STATE_NONE) {
				// Launch the DeviceListActivity to see devices and do scan
				Intent serverIntent = new Intent(this, DeviceListActivity.class);
				startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
				
			} else if (getConnectionState() == BluetoothSerialService.STATE_CONNECTED) {
				mSerialService.stop();
				mSerialService.start();
			}
			return true;
		}
		return false;
	}

	public int getConnectionState() {
		return mSerialService.getState(); 
	}

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(LOG_TAG, "onActivityResult " + resultCode);
        
        switch (requestCode) {
        
        case REQUEST_CONNECT_DEVICE:

            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                // Get the device MAC address
                String address = data.getExtras()
                                     .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                // Get the BLuetoothDevice object
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                // Attempt to connect to the device
                mSerialService.connect(device);                
            }
            break;

        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                Log.d(LOG_TAG, "BT not enabled");
                
                finishDialogNoBluetooth();                
            }
        }
    }
	
	// The Handler that gets information back from the BluetoothService
	private final Handler mHandlerBT = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case Terminal.MESSAGE_STATE_CHANGE:
				Log.i(LOG_TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
				switch (msg.arg1) {
				case BluetoothSerialService.STATE_CONNECTED:
					if (mMenuItemConnect != null) {
						mMenuItemConnect
								.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
						mMenuItemConnect.setTitle(R.string.disconnect);
					}
					
					mTitle.setText(R.string.title_connected_to);
					mTitle.append(mConnectedDeviceName);
					break;

				case BluetoothSerialService.STATE_CONNECTING:
					mTitle.setText(R.string.title_connecting);
					break;

				case BluetoothSerialService.STATE_LISTEN:
				case BluetoothSerialService.STATE_NONE:
					if (mMenuItemConnect != null) {
						mMenuItemConnect
								.setIcon(android.R.drawable.ic_menu_search);
						mMenuItemConnect.setTitle(R.string.connect);
					}
					
					mTitle.setText(R.string.title_not_connected);

					break;
				}
				break;
			case Terminal.MESSAGE_DEVICE_NAME:
				// save the connected device's name
				mConnectedDeviceName = msg.getData().getString(Terminal.DEVICE_NAME);
				Toast.makeText(getApplicationContext(),
						"Connected to " + mConnectedDeviceName,
						Toast.LENGTH_SHORT).show();
				break;
			case Terminal.MESSAGE_TOAST:
				Toast.makeText(getApplicationContext(),
						msg.getData().getString(Terminal.TOAST), Toast.LENGTH_SHORT)
						.show();
				break;
			}
		}
	};

	public void finishDialogNoBluetooth() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.alert_dialog_no_bt)
				.setIcon(android.R.drawable.ic_dialog_info)
				.setTitle(R.string.app_name)
				.setCancelable(false)
				.setPositiveButton(R.string.alert_dialog_ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								finish();
							}
						});
		AlertDialog alert = builder.create();
		alert.show();
	}
}
