package com.red_folder.phonegap.plugin.backgroundservice;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class AMDB extends SQLiteOpenHelper {
	public static final String DATABASE_NAME = "accel.db";
	public static final int DATABASE_VERSION = 2;
	
	public AMDB(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	public void dropDataTable(SQLiteDatabase database) {
		database.execSQL("DROP TABLE IF EXISTS acceldata");
	}
	
	public void createDataTable(SQLiteDatabase database) {
		database.execSQL(
				"CREATE TABLE acceldata (" +
					"record_id integer primary key autoincrement, " +
					"record_time integer not null, " +
					"x real not null, " +
					"y real not null, " +
					"z real not null " +
				")"
			);
	}
	
	@Override
	public void onCreate(SQLiteDatabase database) {
		createDataTable(database);
		database.execSQL(
			"CREATE TABLE accelconfig (" +
				"key text primary key, " +
				"value text not null" +
			")"
		);
	}

	@Override
	public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
		dropDataTable(database);
		database.execSQL("DROP TABLE IF EXISTS accelconfig");
		onCreate(database);
	}
}
