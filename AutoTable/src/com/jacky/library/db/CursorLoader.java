package com.jacky.library.db;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.Loader;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class CursorLoader extends AsyncTaskLoader<Cursor> {
	final Loader<Cursor>.ForceLoadContentObserver mObserver;
	SQLiteDatabase mDb;
	Class<?> mClazz;
	String mWhereClause;
	String[] mWhereArgs;
	Cursor mCursor;

	@Override
	public Cursor loadInBackground() {
		Cursor cursor = 
				mDb == null ? 
				DBHelper.getQueryCursor(mClazz, mWhereClause, mWhereArgs) :
				DBHelper.getQueryCursor(mDb, mClazz, mWhereClause, mWhereArgs);

		if (cursor != null) {
			cursor.getCount();
			cursor.registerContentObserver(this.mObserver);
		}
		return cursor;
	}

	@Override
	public void deliverResult(Cursor cursor) {
		if (isReset()) {
			if (cursor != null) {
				cursor.close();
			}
			return;
		}
		Cursor oldCursor = this.mCursor;
		this.mCursor = cursor;

		if (isStarted()) {
			super.deliverResult(cursor);
		}

		if ((oldCursor != null) && (oldCursor != cursor)
				&& (!oldCursor.isClosed()))
			oldCursor.close();
	}

	public CursorLoader(Context context, Class<?> clazz, String whereClause, String[] whereArgs) {
		this(context, null, clazz, whereClause, whereArgs);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public CursorLoader(Context context,SQLiteDatabase db, Class<?> clazz, String whereClause, String[] whereArgs) {
		super(context);
		this.mObserver = new Loader.ForceLoadContentObserver();

		this.mDb = db;
		this.mClazz = clazz;
		this.mWhereArgs = whereArgs;
		this.mWhereClause = whereClause;
	}

	@Override
	protected void onStartLoading() {
		if (this.mCursor != null) {
			deliverResult(this.mCursor);
		}
		if ((takeContentChanged()) || (this.mCursor == null))
			forceLoad();
	}

	@Override
	protected void onStopLoading() {
		cancelLoad();
	}

	@Override
	public void onCanceled(Cursor cursor) {
		if ((cursor != null) && (!cursor.isClosed()))
			cursor.close();
	}

	@Override
	protected void onReset() {
		super.onReset();

		onStopLoading();

		if ((this.mCursor != null) && (!this.mCursor.isClosed())) {
			this.mCursor.close();
		}
		this.mCursor = null;
	}

	public SQLiteDatabase getDb() {
		return mDb;
	}

	public void setDb(SQLiteDatabase db) {
		this.mDb = db;
	}

	public Class<?> getClazz() {
		return mClazz;
	}

	public void setClazz(Class<?> mClazz) {
		this.mClazz = mClazz;
	}

	public String getWhereClause() {
		return mWhereClause;
	}

	public void setWhereClause(String mWhereClause) {
		this.mWhereClause = mWhereClause;
	}

	public String[] getWhereArgs() {
		return mWhereArgs;
	}

	public void setWhereArgs(String[] mWhereArgs) {
		this.mWhereArgs = mWhereArgs;
	}
	
}