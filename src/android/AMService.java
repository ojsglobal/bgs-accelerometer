package com.red_folder.phonegap.plugin.backgroundservice;

import com.red_folder.phonegap.plugin.backgroundservice.BackgroundService;

import org.json.JSONException;
import org.json.JSONObject;

import android.R;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Notification;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.*;
import android.database.sqlite.*;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class AMService extends BackgroundService implements SensorEventListener {
	private static final String TAG = "AMService";
	
	private int daysToKeep;
	private boolean dataread;
	private SensorManager manager;
	private Sensor am;
	private float x, y, z;
	
	private SQLiteDatabase database;
	private AMDB dbHelper;
	
	private static boolean recording = false;
	
	public AMService() {
		daysToKeep = 1;
		dataread = false;
		x = y = z = 0f;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		Log.d(TAG, "onCreate()");
		
		manager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
		am = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		manager.registerListener(this, am, SensorManager.SENSOR_DELAY_NORMAL);
		
		dbHelper = new AMDB(getBaseContext());
		
		Notification notification = new Notification.Builder(getBaseContext())
			.setContentTitle("Accelerometer notification")
			.setContentText("AMService")
			.setSmallIcon(R.drawable.arrow_down_float)
			//.setContentIntent(pIntent)
			.setAutoCancel(true).build();
			//.addAction(R.drawable.icon, "Call", pIntent)
			//.addAction(R.drawable.icon, "More", pIntent)
			//.addAction(R.drawable.icon, "And more", pIntent).build();
		this.startForeground(1, notification);
		
		database = dbHelper.getReadableDatabase();
	    Cursor cursor = database.query("accelconfig",
	            new String[] { "key", "value" },
	            null, null, null, null, null);
	    cursor.moveToFirst();
	    while(!cursor.isAfterLast()) {
	    	if(cursor.getString(0).equals("recording")) {
	    		String value = cursor.getString(1);
	    		recording = (value.charAt(0) != '0');
	    	}
	    	cursor.moveToNext();
	    }
	}
	
	@Override
	protected JSONObject initialiseLatestResult() {
		return null;
	}

	@Override
	protected JSONObject doWork() {
		JSONObject result = new JSONObject();

		try {
			/*
			// Following three lines simply produce a text string with Hello World and the date & time (UK format)
			SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss"); 
			String now = df.format(new Date(System.currentTimeMillis())); 
			String msg = "Hello World - its " + now;

			// We output the message to the logcat
			Log.d("MyService", msg);

			// We also provide the same message in our JSON Result
			result.put("Message", msg);
			*/
			
			if(recording) {
				try {
					database = dbHelper.getWritableDatabase();
					ContentValues values = new ContentValues();
					values.put("record_time", System.currentTimeMillis());
					values.put("x", x);
					values.put("y", y);
					values.put("z", z);
					database.insert("acceldata", null, values);
				
					long maxTime = System.currentTimeMillis() - (daysToKeep * (24 * 60 * 60 * 1000));
					database.delete("acceldata", "record_time<" + maxTime, null);
				} catch(Exception e) {
					result.put("error", e.toString());
				}
			}
			
			if(!dataread)
				return null;
			
			result.put("recording", recording);
			result.put("time", System.currentTimeMillis());
			result.put("X", Float.toString(x));
			result.put("Y", Float.toString(y));
			result.put("Z", Float.toString(z));
		} catch(JSONException e) {
			try {
				result.put("error", e.getMessage());
			} catch (JSONException _e) {
			}
		}

		return result;   
	}

	@Override
	protected JSONObject getConfig() {
		JSONObject result = new JSONObject();

		try {
			if(dataread) {
				result.put("records", 1);
				int i = 0;
				database = dbHelper.getReadableDatabase();
			    Cursor cursor = database.query("acceldata",
			            new String[] { "record_id", "record_time", "x", "y", "z" },
			            null, null, null, null, null);
			    cursor.moveToFirst();
			    while(!cursor.isAfterLast()) {
			    	String n = Integer.toString(i);
			    	result.put("id" + n, cursor.getLong(0));
			    	result.put("time" + n, cursor.getLong(1));
			    	result.put("x" + n, cursor.getDouble(2));
			    	result.put("y" + n, cursor.getDouble(3));
			    	result.put("z" + n, cursor.getDouble(4));
			    	cursor.moveToNext();
			    	i++;
			    }
			} else
				result.put("records", "no");
		} catch(Exception e) {
		}

		return result;
	}

	@Override
	protected void setConfig(JSONObject config) {
		try {
			if(config.has("daysToKeep"))
				daysToKeep = config.getInt("daysToKeep");
			if(config.has("recording")) {
				recording = (config.getInt("recording") != 0);
				database = dbHelper.getWritableDatabase();
				database.delete("accelconfig", "key='recording'", null);
				ContentValues values = new ContentValues();
				values.put("key", "recording");
				values.put("value", (recording ? "1" : "0"));
				database.insert("accelconfig", null, values);
			}
			if(config.has("dataread"))
				dataread = (config.getInt("dataread") != 0);
			if(config.has("dataclear")) {
				dbHelper.dropDataTable(database);
				dbHelper.createDataTable(database);
			}
		} catch(JSONException e) {
		}
	}

	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		x = event.values[0];
		y = event.values[1];
		z = event.values[2];
	}
}
