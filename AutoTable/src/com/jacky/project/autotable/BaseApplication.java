package com.jacky.project.autotable;

import android.app.Application;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

/**
 * Created by Administrator on 2014-11-20.
 */
public class BaseApplication extends Application{

    protected static BaseApplication mApplication;
    protected SQLiteDatabase mDBHelper;

    public static BaseApplication getInstance(){
        return mApplication;
    }

    public void onCreate() {
        super.onCreate();

        mApplication = this;
        mDBHelper = openOrCreateDatabase("autotable.db", Context.MODE_PRIVATE, null);
    }

    public static SQLiteDatabase getDefaultSqliteDatabase() {
        return getInstance().mDBHelper;
    }
}
