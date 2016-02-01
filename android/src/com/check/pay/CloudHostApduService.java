package com.check.pay;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import au.com.bellsolutions.android.card.ISO7816Card;
import au.com.bellsolutions.android.emv.util.HexString;
import au.com.bellsolutions.android.util.net.HttpRequestHandler;

import android.content.Context;
import android.content.SharedPreferences;
import android.nfc.cardemulation.HostApduService;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

public class CloudHostApduService extends HostApduService {
	private PowerManager.WakeLock mWakeLock = null;
	private boolean mCloud = true;
	private static final byte[] error = { 0x6f, 0x00 };
	private static final byte[] selectResponse = { 0x6f, 0x47, (byte) 0x84,
			0x07, (byte) 0xa0, 0x00, 0x00, 0x00, 0x03, 0x10, 0x10, (byte) 0xa5,
			0x3c, 0x50, 0x0e, 0x4e, 0x41, 0x42, 0x20, 0x56, 0x69, 0x73, 0x61,
			0x20, 0x44, 0x65, 0x62, 0x69, 0x74, (byte) 0x87, 0x01, 0x01,
			(byte) 0x9f, 0x12, 0x0e, 0x4e, 0x41, 0x42, 0x20, 0x56, 0x69, 0x73,
			0x61, 0x20, 0x44, 0x65, 0x62, 0x69, 0x74, (byte) 0x9f, 0x11, 0x01,
			0x01, 0x5f, 0x2d, 0x02, 0x65, 0x6e, (byte) 0x9f, 0x38, 0x0c,
			(byte) 0x9f, 0x66, 0x04, (byte) 0x9f, 0x02, 0x06, (byte) 0x9f,
			0x37, 0x04, 0x5f, 0x2a, 0x02, (byte) 0x90, 0x00 };

	private static final byte[] gpoResponse = { (byte) 0x80, 0x06, 0x00,
			(byte) 0x80, 0x08, 0x01, 0x01, 0x00, (byte) 0x90, 0x00 };
	private static final byte[] ppseResponse = { 0x6F, 0x30, (byte) 0x84, 0x0E,
			0x32, 0x50, 0x41, 0x59, 0x2E, 0x53, 0x59, 0x53, 0x2E, 0x44, 0x44,
			0x46, 0x30, 0x31, (byte) 0xA5, 0x1E, (byte) 0xBF, 0x0C, 0x1B, 0x61,
			0x19, 0x4F, 0x07, (byte) 0xA0, 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
			0x50, 0x0B, 0x56, 0x49, 0x53, 0x41, 0x20, 0x43, 0x52, 0x45, 0x44,
			0x49, 0x54, (byte) 0x87, 0x01, 0x01, (byte) 0x90, 0x00 };
	private static final String TAG = "CloudHostApduService";
	
	@Override
	public void onCreate() {
		super.onCreate();
		getLock();
	}
	
	private void getLock() {
		if (mWakeLock == null) {
			Log.d(TAG, "Create WL");
			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "My Tag");
		}
	}

	@Override
	public void onDeactivated(int reason) {
		Toast.makeText(this, "Transaction Complete"+reason+" "+String.valueOf(reason), Toast.LENGTH_LONG).show();
	}

	@Override
	public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
//		if (mCloud) {
//			getLock();
//			mWakeLock.acquire();
//			Log.d(TAG, "Lock on");
//			new CloudTask().execute(commandApdu);
//			return null;
//		} else {
			byte CMD = commandApdu[0];
			byte INS = commandApdu[1];
			byte LC = commandApdu[4];
			byte[] data = new byte[LC];
			System.arraycopy(commandApdu, 5, data, 0, LC);

			if (CMD == 0x00) {
				// check if this is select
				if (INS == (byte) 0xA4) {
					// select command - check data
					if (data[0] == 0x32) {
						// select PPSE
						return ppseResponse;
					} else {
						// select VMPA
						return selectResponse;
					}
				} else if (INS == (byte) 0xb2) {
					SharedPreferences prefs = getSharedPreferences(
							MainActivity.TAG, MODE_PRIVATE);
					String msd = prefs.getString(MainActivity.PREF_MSD,
							"123456789000");
					return HexString.parseHexString(msd);
					// read record
					// return recordContents;
				} else {
					return error;
				}
			} else {
				if (INS == (byte) 0xA8) {
					// GPO
					return gpoResponse;
				}
				return error;
			}
//		}
	}
	
	public void handleResponse(byte[] apdu) {
		this.sendResponseApdu(apdu);
		mWakeLock.release();
		Log.d(TAG, "Lock off");
	}

	private class CloudTask extends AsyncTask<byte[], Void, Void> {

		@Override
		protected Void doInBackground(byte[]... params) {
			CloudSecureElement cloudElement = new CloudSecureElement();
			try {
				cloudElement.connect();
				handleResponse(cloudElement.send(params[0]));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				handleResponse(HexString.parseHexString("6F00"));
			}
			return null;
		}
	}

	/*
	 * CloudSecureElement proxies messages to the SE Cloud Server.
	 */
	private class CloudSecureElement implements ISO7816Card.CardSpec {
		HttpRequestHandler mHttp;
		String mUrl = "http://bondi.getmyip.com:5649";

		@Override
		public void connect() throws IOException {
			mHttp = HttpRequestHandler.getInstance();
			return;
		}

		@Override
		public void close() throws IOException {
			mHttp = null;
			return;
		}

		@Override
		public byte[] send(byte[] apdu) throws IOException {
			// convert apdu to hex string
			String hexApdu = HexString.hexify(apdu);
			// pass to the server
			String json = mHttp.get(mUrl, "CardServer/Server?apdu=" + hexApdu);
			// parse the response
			try {
				JSONObject js = new JSONObject(json);
				String response = js.getString("response");
				String sw = js.getString("sw");
				// send back to reader
				if (!response.isEmpty()) {
					return HexString.parseHexString(response + sw);
				} else {
					return HexString.parseHexString(sw);
				}
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return HexString.parseHexString("6F00");
			}
		}

	}
}
