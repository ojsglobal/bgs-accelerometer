package com.winchesterdavenport.bgs.accelerometer;

import com.red_folder.phonegap.plugin.backgroundservice.BackgroundService;

import org.json.JSONException;
import org.json.JSONObject;

import android.R;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.lang.Math.*;

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

//import android.content.pm.PackageManager;

public class AMService extends BackgroundService implements SensorEventListener {
	private static final String TAG = "AMService";
	
	private int daysToKeep;
	private boolean dataread = true;
	private SensorManager manager;
	private Sensor am;
	private double x, y, z;
	private int steps = 0;
	private double fltMagnitude = 0;
	private double fltLastAccel = 0;
	private int intMillisecondCount = 0;

	private SQLiteDatabase database;
	private AMDB dbHelper;
	

	private float  	mLimit = 10;
	private float   mLastValues[] = new float[3*2];
	private float   mScale[] = new float[2];
	private float   mYOffset;
	
	private float   mLastDirections[] = new float[3*2];
	private float   mLastExtremes[][] = { new float[3*2], new float[3*2] };
	private float   mLastDiff[] = new float[3*2];
	private int     mLastMatch = -1;
	


	private static boolean recording = true;
	
	public AMService() 
	{
		daysToKeep = 14;
		dataread = true;
		x = y = z = 0f;
		steps = 0;		

		int h = 480; // TODO: remove this constant
		mYOffset = h * 0.5f;
		mScale[0] = - (h * 0.5f * (1.0f / (SensorManager.STANDARD_GRAVITY * 2)));
		mScale[1] = - (h * 0.5f * (1.0f / (SensorManager.MAGNETIC_FIELD_EARTH_MAX)));

		//SENSITIVITY
		mLimit = 10; // 1.97  2.96  4.44  6.66  10.00  15.00  22.50  33.75  50.62
  


	}

	@Override
	public void onCreate() {
		super.onCreate();
		
		//PackageManager pm = getPackageManager();
        	//Intent intent = pm.getLaunchIntentForPackage(getApplicationContext().getPackageName());
        
		Log.d(TAG, "onCreate()");
		
		manager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
		am = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		
		//am = manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
		
		manager.registerListener(this, am, SensorManager.SENSOR_DELAY_FASTEST);
		
		dbHelper = new AMDB(getBaseContext());
		
		Notification notification = new Notification.Builder(getBaseContext())
			.setContentTitle("Healthia")
			.setContentText("Working correctly.")
			.setSmallIcon(R.drawable.arrow_down_float)
			.setAutoCancel(true).build();

		this.startForeground(1, notification);
		
		int milliseconds = 100; 
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

			if(cursor.getString(0).equals("milliseconds")) 
			{ 
 		    		Integer value = cursor.getInt(1); 
 				    milliseconds = value.intValue(); 
 			} 

	    	cursor.moveToNext();
	    }

		setMilliseconds(milliseconds); 
 		restartTimer(); 

	}
	
	@Override
	protected JSONObject initialiseLatestResult() {
		return null;
	}

	@Override
	protected JSONObject doWork() {
		JSONObject result = new JSONObject();

		try {
			
			if(recording) 
			{
				try 
				{


					intMillisecondCount++;

					//Millisecond count is set to 600 (one minute), when it reaches this, we know a minute has passed, and so should store the step count, and reset intMillisecondCount and steps.
					if (intMillisecondCount >= 600)
					{

						intMillisecondCount = 0;
						
						if (steps > 0)
						{
							database = dbHelper.getWritableDatabase();
							ContentValues values = new ContentValues();
							values.put("record_time", System.currentTimeMillis());
							values.put("x", x);
							values.put("y", y);
							values.put("z", z);
							values.put("steps", steps);
							database.insert("acceldata", null, values);

							steps = 0;
						}
						

					}
					
					long maxTime = System.currentTimeMillis() - (daysToKeep * (24 * 60 * 60 * 1000));
					database.delete("acceldata", "record_time<" + maxTime, null);
				
				}catch(Exception e) 
				{
					result.put("error", e.toString());
				}
			}
			
			if(!dataread)
				return null;
		

		} catch(JSONException e) {
			try {
				result.put("error", e.getMessage());
			} catch (JSONException _e) {
			}
		}

		return result;   
	}

	@Override
	protected JSONObject getConfig() 
	{
		JSONObject result = new JSONObject();

		try {
			if(dataread) {
				result.put("records", 1);
				int i = 0;
				database = dbHelper.getReadableDatabase();
			    Cursor cursor = database.query("acceldata",
			            new String[] { "record_id", "record_time", "x", "y", "z", "steps" },
			            null, null, null, null, null);
			    cursor.moveToFirst();
			    while(!cursor.isAfterLast()) {
			    	String n = Integer.toString(i);
			    	result.put("id" + n, cursor.getLong(0));
			    	result.put("time" + n, cursor.getLong(1));
			    	result.put("x" + n, cursor.getDouble(2));
			    	result.put("y" + n, cursor.getDouble(3));
			    	result.put("z" + n, cursor.getDouble(4));
					result.put("steps" + n, cursor.getDouble(5));
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
	protected void setConfig(JSONObject config) 
	{
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

	/*@Override
	public void onSensorChanged(SensorEvent event) 
	{
			x = event.values[0];
			y = event.values[1];
			z = event.values[2];
	
			fltMagnitude = Math.sqrt(Math.pow(x,2) + Math.pow(y,2) + Math.pow(z,2));	
			
			if(fltLastAccel < 9 && fltMagnitude > 9.68) steps++;
			fltLastAccel = fltMagnitude;
	
		
	//            steps++;
	        
        
	}
*/

	@Override
	public void onSensorChanged(SensorEvent event) 
	{

	    Sensor sensor = event.sensor; 
        synchronized (this)
		{
            if (sensor.getType() == Sensor.TYPE_ORIENTATION) 
			{
            }
            else 
			{
                int j = (sensor.getType() == Sensor.TYPE_ACCELEROMETER) ? 1 : 0;
                if (j == 1) 
				{
                    float vSum = 0;
                    for (int i=0 ; i<3 ; i++) 
					{
                        final float v = mYOffset + event.values[i] * mScale[j];
                        vSum += v;
                    }
                    int k = 0;
                    float v = vSum / 3;
                    
                    float direction = (v > mLastValues[k] ? 1 : (v < mLastValues[k] ? -1 : 0));
                    if (direction == - mLastDirections[k])
					{
                        // Direction changed
                        int extType = (direction > 0 ? 0 : 1); // minumum or maximum?
                        mLastExtremes[extType][k] = mLastValues[k];
                        float diff = Math.abs(mLastExtremes[extType][k] - mLastExtremes[1 - extType][k]);

                        if (diff > mLimit) 
						{
                            
                            boolean isAlmostAsLargeAsPrevious = diff > (mLastDiff[k]*2/3);
                            boolean isPreviousLargeEnough = mLastDiff[k] > (diff/3);
                            boolean isNotContra = (mLastMatch != 1 - extType);
                            
                            if (isAlmostAsLargeAsPrevious && isPreviousLargeEnough && isNotContra) 
							{
								steps++;   
                                mLastMatch = extType;
                            }
                            else 
							{
                                mLastMatch = -1;
                            }
                        }
                        mLastDiff[k] = diff;
                    }
                    mLastDirections[k] = direction;
                    mLastValues[k] = v;
                }
            }
        }
    }

}
