package com.check.pay;

import java.io.IOException;
import java.util.Hashtable;

import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import au.com.bellsolutions.android.card.ISO7816Card.StatusWordException;
import au.com.bellsolutions.android.card.hardware.ExternalCard;
import au.com.bellsolutions.android.emv.EmvCard;
import au.com.bellsolutions.android.emv.Terminal;
import au.com.bellsolutions.android.emv.util.TlvException;

public class PerformTransactionTask extends AsyncTask<Tag, Integer, Bundle> {
	private static final String TAG = "PerformTransactionTask";
	private String mAmt;
	private MainActivity linkedActivity = null;
	
	public PerformTransactionTask(MainActivity linkedActivity, String amt) {
		mAmt = amt;
		Log.d(TAG, "Amt: " + mAmt);
		attach(linkedActivity);
	}
	
	public void attach(MainActivity linkedActivity) {
		this.linkedActivity = linkedActivity;
		
		Log.d(TAG, "Activity link complete");
	}
	
	public void detach() {
		this.linkedActivity = null;
	}
	
	private String padAmount(String amt) {
		String unpadded = amt.replace(".", "");
		// pad amount to correct length
		while (unpadded.length() < 12) {
			unpadded = "0" + unpadded;
		}
		return unpadded;
	}
	
	@Override
	protected Bundle doInBackground(Tag... tags) {
		Terminal term = null;
		String pan = "";
		Bundle b = new Bundle();
		try {
			// get amt of transaction
			String amt = padAmount(mAmt);
			Log.d(TAG, "Amt " + amt);

			Hashtable<String, String> terminalSettings = new Hashtable<String, String>();
			terminalSettings.put("9F02", amt);					// Amt Authorised
			
			term = new Terminal(new EmvCard(new ExternalCard(tags[0])), terminalSettings);
		
			term.connect();
			publishProgress(0);
			term.selectPpse();
			term.selectPaymentApp();
			term.getProcessingOptions();
			term.readRecords();
			pan = term.getPAN();
			
			
			if (!pan.isEmpty()) {
				publishProgress(1);
				term.writeCardContentsToBundle(b);
				b.putString("result", pan);
				b.putString("error", "no_error");
				
			}
		} catch (StatusWordException e) {
			e.printStackTrace();
			b.putString("error", e.getMessage());
		} catch (IOException e) {
			e.printStackTrace();
			b.putString("error", e.getMessage());
		} catch (TlvException e) {
			e.printStackTrace();
			b.putString("error", e.getMessage());
		} catch (IllegalStateException e) {
			e.printStackTrace();
			b.putString("error", e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
			b.putString("error", e.getMessage());
		} finally {
			try {
				term.disconnect();
			} catch (Exception e) {
				e.printStackTrace();
			}	
		}
		return b;
	}
	
	@Override
	protected void onProgressUpdate(Integer...status) {
		linkedActivity.onProgressUpdate(status[0]);
	}
	
	@Override
	protected void onPostExecute(Bundle result) {
		linkedActivity.onTaskFinished(result);
	}
	
	@Override
	protected void onCancelled() {
        linkedActivity.onCancelled();
	}
	
	@Override
	public void finalize() {
		try {
			super.finalize();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
	
}
