package com.example.hydro;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public class MainActivity extends Activity {

	private static final String TAG = "BlueTest5-MainActivity";
	private int mMaxChars = 50000; // Default
	private UUID mDeviceUUID;
	private BluetoothSocket mBTSocket;
	private ReadInput mReadThread = null;

	private boolean mIsUserInitiatedDisconnect = false;

	// All controls here
	private TextView mTxtReceive;
	private EditText mEditSend;
	private Button mBtnDisconnect;
	private Button mBtnClear; // Add this button
	private ScrollView scrollView;
	private CheckBox chkScroll;
	private CheckBox chkReceiveText;

	private boolean mIsBluetoothConnected = false;

	private BluetoothDevice mDevice;

	private ProgressDialog progressDialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		ActivityHelper.initialize(this);

		Intent intent = getIntent();
		Bundle b = intent.getExtras();
		mDevice = b.getParcelable(Homescreen.DEVICE_EXTRA);
		mDeviceUUID = UUID.fromString(b.getString(Homescreen.DEVICE_UUID));
		mMaxChars = b.getInt(Homescreen.BUFFER_SIZE);

		Log.d(TAG, "Ready");

		mBtnDisconnect = (Button) findViewById(R.id.btnDisconnect);
		mTxtReceive = (TextView) findViewById(R.id.txtReceive);
		mBtnClear = (Button) findViewById(R.id.btnClear); // Initialize the "Clear" button
		scrollView = (ScrollView) findViewById(R.id.viewScroll);
		chkScroll = (CheckBox) findViewById(R.id.chkScroll);
		chkReceiveText = (CheckBox) findViewById(R.id.chkReceiveText);

		mTxtReceive.setMovementMethod(new ScrollingMovementMethod());

		mBtnDisconnect.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mIsUserInitiatedDisconnect = true;
				new DisConnectBT().execute();
			}
		});

		mBtnClear.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				clearReceivedData();
			}
		});
	}

	private class ReadInput implements Runnable {
		private boolean bStop = false;
		private Thread t;

		public ReadInput() {
			t = new Thread(this, "Input Thread");
			t.start();
		}

		public boolean isRunning() {
			return t.isAlive();
		}

		@Override
		public void run() {
			InputStream inputStream;

			try {
				inputStream = mBTSocket.getInputStream();
				while (!bStop) {
					byte[] buffer = new byte[256];
					if (inputStream.available() > 0) {
						inputStream.read(buffer);
						int i = 0;
						for (i = 0; i < buffer.length && buffer[i] != 0; i++) {
						}
						final String strInput = new String(buffer, 0, i);

						if (chkReceiveText.isChecked()) {
							mTxtReceive.post(new Runnable() {
								@Override
								public void run() {
									mTxtReceive.append(strInput);

									int txtLength = mTxtReceive.getEditableText().length();
									if (txtLength > mMaxChars) {
										mTxtReceive.getEditableText().delete(0, txtLength - mMaxChars);
									}

									if (chkScroll.isChecked()) {
										scrollView.post(new Runnable() {
											@Override
											public void run() {
												scrollView.fullScroll(View.FOCUS_DOWN);
											}
										});
									}
								}
							});
						}
					}
					Thread.sleep(500);
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		public void stop() {
			bStop = true;
		}
	}

	private class DisConnectBT extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... params) {
			if (mReadThread != null) {
				mReadThread.stop();
				while (mReadThread.isRunning()) ;
				mReadThread = null;
			}

			try {
				mBTSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			mIsBluetoothConnected = false;
			if (mIsUserInitiatedDisconnect) {
				finish();
			}
		}
	}

	private void clearReceivedData() {
		mTxtReceive.setText(""); // Clears the text in the TextView
	}

	private void msg(String s) {
		Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
	}

	@Override
	protected void onPause() {
		if (mBTSocket != null && mIsBluetoothConnected) {
			new DisConnectBT().execute();
		}
		Log.d(TAG, "Paused");
		super.onPause();
	}

	@Override
	protected void onResume() {
		if (mBTSocket == null || !mIsBluetoothConnected) {
			new ConnectBT().execute();
		}
		Log.d(TAG, "Resumed");
		super.onResume();
	}

	@Override
	protected void onStop() {
		Log.d(TAG, "Stopped");
		super.onStop();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}

	private class ConnectBT extends AsyncTask<Void, Void, Void> {
		private boolean mConnectSuccessful = true;

		@Override
		protected void onPreExecute() {
			progressDialog = ProgressDialog.show(MainActivity.this, "Hold on", "Connecting");
		}

		@Override
		protected Void doInBackground(Void... devices) {
			try {
				if (mBTSocket == null || !mIsBluetoothConnected) {
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD_MR1) {
						mBTSocket = mDevice.createInsecureRfcommSocketToServiceRecord(mDeviceUUID);
					}
					BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
					mBTSocket.connect();
				}
			} catch (IOException e) {
				e.printStackTrace();
				mConnectSuccessful = false;
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			if (!mConnectSuccessful) {
				Toast.makeText(getApplicationContext(), "Could not connect to device. Is it a Serial device? Also check if the UUID is correct in the settings", Toast.LENGTH_LONG).show();
				finish();
			} else {
				msg("Connected to device");
				mIsBluetoothConnected = true;
				mReadThread = new ReadInput();
			}

			progressDialog.dismiss();
		}
	}
}
