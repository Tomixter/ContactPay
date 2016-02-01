package com.check.pay;

import java.text.NumberFormat;
import java.util.Enumeration;
import java.util.Hashtable;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import au.com.bellsolutions.android.emv.Terminal.AflItem;
import au.com.bellsolutions.android.emv.util.HexString;
import au.com.bellsolutions.android.emv.util.Tlv;
import au.com.bellsolutions.android.nfc.activity.BaseNfcActivity;

public class MainActivity extends BaseNfcActivity {
    public static final String TAG = "NfcActivity";
	private static final String STATE_AMT = "com.visa.AMT";
	private static final String STATE_INFO = "com.visa.INFO";
	static final String PREF_MSD = "au.com.bellsolutions.android.hce.MSD";
	static final String PREF_PAN = "au.com.bellsolutions.android.hce.PAN";
	public static final int REQ_READ_CARD = 1;
	public static final int RSP_OK = 0;
	public static final int RSP_ERROR = 1;
	public static final int RSP_CANCEL = 2;
	public static final int STATUS_READING_CARD = 1;
	public static final int STATUS_ONLINE = 2;
	private TextView mInfo;
	private String mAmt="0.20";
	private PerformTransactionTask mRunningTask;
	private TextView mLog;
	private ImageView mRipple;
	private Animation mFade;
	
	
	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate");
		
		setContentView(R.layout.activity_main);
		
		if (getIntent().getExtras() != null) {
			Log.d(TAG, "with extras");
//			mAmt = getIntent().getExtras().getString("amt");
			mAmt = "0.20";
		} else {
			mAmt = "0.20";
		}
		
		if(mAmt == null || mAmt.equalsIgnoreCase("")){
			mAmt = "0.20";
		}
		
		if( (mRunningTask = (PerformTransactionTask)getLastNonConfigurationInstance()) != null) {
			Log.d(TAG, "re-attach to previous task"); 
			mRunningTask.attach(this);
		} else {
			Log.d(TAG, "new task");
			mRunningTask = new PerformTransactionTask(this, mAmt);
		}

		mInfo = (TextView) findViewById(R.id.tvinfo);
		//mInfo.setText(getString(R.string.txt_tap_card));
		mLog = (TextView) findViewById(R.id.log);
		mRipple = (ImageView) findViewById(R.id.imageView1);
		mFade = AnimationUtils.loadAnimation(this, R.anim.fadein);
	    
		
		if (savedInstanceState != null) {
			Log.d(TAG, "update from saved instance");
//			mAmt = savedInstanceState.getString(STATE_AMT);
			mInfo.setText(savedInstanceState.getString(STATE_INFO));
			mAmt = "0.20";
		}
		
		setResult(RSP_CANCEL);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(STATE_AMT, mAmt);
		outState.putString(STATE_INFO, mInfo.getText().toString());
	}
	
	@Override
	public Object onRetainNonConfigurationInstance() {
		Log.d(TAG, "config change. detach from task");
		mRunningTask.detach();
		return mRunningTask;
	}
	
	@Override
	public void onPause() {
		super.onPause();
		Log.d(TAG, "onPause");
		//mFade.reset();
	}
	
	@Override
	public void onResume() {
		super.onResume();
		Log.d(TAG, "onResume");
		mRipple.startAnimation(mFade);
	}
	
	public void onNfcDiscovered(Tag tag) {
		// Make processing a transaction one shot for this activity
		if (mRunningTask.getStatus().equals(AsyncTask.Status.PENDING)) {
			Log.d(TAG, "task pending so execute");
			mRunningTask.execute(tag);
		} else {
			Log.d(TAG, "no task pending");
			Log.d(TAG, "new task");
			mRunningTask = new PerformTransactionTask(this, mAmt);
			mRunningTask.execute(tag);
		}
	}
	
	public void onProgressUpdate(Integer status) {
		switch (status) {
			case 0: mInfo.setText(getString(R.string.txt_reading_card)); break;
			case 1: mInfo.setText(getString(R.string.txt_online_processing)); break;
		}
	}
	
	public void onCancelled() {
		setResult(RSP_CANCEL);
        finish();
	}
	
	@SuppressWarnings("unchecked")
	public void onTaskFinished(Bundle bundle) {
		try {
			String result = bundle.getString("result");
			if (result != null) {
				mInfo.setText(result);
				showLog(bundle);
				Hashtable<AflItem, byte[]> recordResponses = (Hashtable<AflItem, byte[]>) bundle.getSerializable("records");
				SharedPreferences prefs = getSharedPreferences(TAG, MODE_PRIVATE);
				Editor edit = prefs.edit();
				edit.putString(PREF_MSD, HexString.hexify(recordResponses.values().iterator().next()));
				edit.commit();
			} else {
				String error = bundle.getString("error");
				if (error != null) {
					mInfo.setText(error);
				} else {
					mInfo.setText("Unknown error");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			mInfo.setText("Error");
		}
	}
	
	@SuppressWarnings("unchecked")
	private void showLog(Bundle b) {
		Hashtable<String, Tlv> cardContents = (Hashtable<String, Tlv>) b.getSerializable("cardcontents");
		Hashtable<String, Tlv> cardSignedContents = (Hashtable<String, Tlv>) b.getSerializable("cardsignedcontents");
		
		mLog.setText("Card Contents\r\n");
		Enumeration<Tlv> e = cardContents.elements();
		while (e.hasMoreElements()) {
			Tlv t = e.nextElement();
			mLog.append(HexString.hexify(t.getBuffer()) + "\r\n");
		}
		mLog.append("Card Signed Contents\r\n");
		e = cardSignedContents.elements();
		while (e.hasMoreElements()) {
			Tlv t = e.nextElement();
			mLog.append(HexString.hexify(t.getBuffer()) + "\r\n");
		}
		
	}
	
	public static String formatCurrency(double amt) {
		NumberFormat formatter = NumberFormat.getCurrencyInstance();
		return formatter.format(amt);
	}
	
	public static String formatCurrency(String amt) {
		return formatCurrency(Double.parseDouble(amt));
	}
}

	


