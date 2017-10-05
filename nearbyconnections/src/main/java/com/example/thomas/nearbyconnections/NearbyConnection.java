package com.example.thomas.nearbyconnections;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.Connections;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by Thomas on 05.06.2016.
 */
public class NearbyConnection implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, Connections.MessageListener
{
    protected final GoogleApiClient mGoogleApiClient;
    protected Context context;
    private BytesReceivedListener onMessageReceivedListener;


    public boolean isRunning()
    {
        return running;
    }

    protected boolean running;

    public NearbyConnection(Context context)
    {
        this.context = context;
        Integer resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(context);
        if (resultCode == ConnectionResult.SUCCESS) {
            //Do what you want
        } else {
            Dialog dialog = GooglePlayServicesUtil.getErrorDialog(resultCode, (Activity) context, 0);
            if (dialog != null) {
                //This dialog will help the user update to the latest GooglePlayServices
                dialog.show();
            }
        }

        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Nearby.CONNECTIONS_API)
                .build();

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(new Runnable()
        {
            @Override
            public void run()
            {
                if (running && !mGoogleApiClient.isConnected())
                {
                    mGoogleApiClient.connect();
                }

            }
        }, 1000, 200, TimeUnit.MILLISECONDS);
    }

    private static int[] NETWORK_TYPES = {ConnectivityManager.TYPE_WIFI,
            ConnectivityManager.TYPE_ETHERNET};

    public boolean isConnectedToNetwork() {
        ConnectivityManager connManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        for (int networkType : NETWORK_TYPES) {
            NetworkInfo info = connManager.getNetworkInfo(networkType);
            if (info != null && info.isConnectedOrConnecting()) {
                return true;
            }
        }
        return false;
    }

    public void start()
    {
        running = true;
        mGoogleApiClient.connect();
    }

    public void stop()
    {
        running = false;
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            Nearby.Connections.stopAllEndpoints(mGoogleApiClient);
            mGoogleApiClient.disconnect();

        }
    }
    public void sendBytes(byte[] bytes, String endPointId)
    {
        Log.d("MAIN", "Sending bytes");
        Nearby.Connections.sendReliableMessage(mGoogleApiClient, endPointId, bytes);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle)
    {
        Log.d("MAIN", "OnConnected");
    }

    @Override
    public void onConnectionSuspended(int i)
    {
        Log.d("MAIN", "OnConnectionSuspended: " + i);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult)
    {
        Log.d("MAIN", "Connection Failed");
    }

    public void setOnMessageReceivedListener(BytesReceivedListener onMessageReceivedListener)
    {
        this.onMessageReceivedListener = onMessageReceivedListener;
    }

    @Override
    public void onMessageReceived(String endPointId, byte[] bytes, boolean reliable)
    {
        if (onMessageReceivedListener != null)
        {
            onMessageReceivedListener.messageReceived(bytes, endPointId);
        }
    }

    @Override
    public void onDisconnected(String s)
    {
        Log.d("NearbyConnection", "On Disconnected: " + s);
    }
}
