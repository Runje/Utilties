package com.example.thomas.nearbyconnections;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AppIdentifier;
import com.google.android.gms.nearby.connection.AppMetadata;
import com.google.android.gms.nearby.connection.Connections;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Thomas on 05.06.2016.
 */
public class NearbyHost extends NearbyConnection implements Connections.ConnectionRequestListener
{
    public NearbyHost(Context context)
    {
        super(context);
    }

    //test
    private void startAdvertising() {
        if (!isConnectedToNetwork()) {
            // Implement logic when device is not connected to a network
            Log.e("MAIN", "NOT CONNECTED TO NETWORK");
            return;
        }

        Log.d("Host", "Start advertising");

        // Advertising with an AppIdentifer lets other devices on the
        // network discover this application and prompt the user to
        // install the application.
        List<AppIdentifier> appIdentifierList = new ArrayList<>();
        appIdentifierList.add(new AppIdentifier(context.getPackageName()));
        AppMetadata appMetadata = new AppMetadata(appIdentifierList);

        // The advertising timeout is set to run indefinitely
        // Positive values represent timeout in milliseconds
        long NO_TIMEOUT = 0L;

        String name = null;
        Nearby.Connections.startAdvertising(mGoogleApiClient, name, appMetadata, NO_TIMEOUT,
                this).setResultCallback(new ResultCallback<Connections.StartAdvertisingResult>() {
            @Override
            public void onResult(Connections.StartAdvertisingResult result) {
                if (result.getStatus().isSuccess()) {
                    // Device is advertising
                    Log.d("Host", "Device is advertising");
                } else {
                    int statusCode = result.getStatus().getStatusCode();
                    // Advertising failed - see statusCode for more details
                    Log.e("Host", "Device is NOT advertising");
                }
            }
        });
    }

    @Override
    public void onConnected(@Nullable Bundle bundle)
    {
        super.onConnected(bundle);
        startAdvertising();
    }

    @Override
    public void onConnectionRequest(final String remoteEndpointId, String remoteDeviceId,
                                    final String remoteEndpointName, byte[] payload) {
        Log.d("Host", "Connection request");
        byte[] myPayload = null;
        // Automatically accept all requests
        Nearby.Connections.acceptConnectionRequest(mGoogleApiClient, remoteEndpointId,
                myPayload, this).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
                if (status.isSuccess()) {
                    Toast.makeText(context, "Connected to " + remoteEndpointName,
                            Toast.LENGTH_SHORT).show();

                } else {
                    Toast.makeText(context, "Failed to connect to: " + remoteEndpointName,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }



    @Override
    public void onDisconnected(String endPointId)
    {

    }
}
