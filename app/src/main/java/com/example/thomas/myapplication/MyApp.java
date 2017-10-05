package com.example.thomas.myapplication;

import android.app.Application;

import net.danlew.android.joda.JodaTimeAndroid;

/**
 * Created by Thomas on 31.01.2016.
 */
public class MyApp extends Application
{
    @Override
    public void onCreate()
    {
        super.onCreate();
        JodaTimeAndroid.init(this);
    }
}
