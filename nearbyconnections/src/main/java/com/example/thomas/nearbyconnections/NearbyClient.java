package com.example.thomas.nearbyconnections;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.Connections;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by Thomas on 05.06.2016.
 */
public class NearbyClient extends NearbyConnection implements Connections.EndpointDiscoveryListener, Connections.MessageListener
{
    private final ScheduledExecutorService scheduler;
    private String hostId;
    private boolean connectedToHost;
    private boolean discovering;
    private boolean connecting;
    private OnConnectedListener onConnectedListener;

    public NearbyClient(Context context)
    {
        super(context);
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(new Runnable()
        {
            @Override
            public void run()
            {
                if (running && mGoogleApiClient.isConnected() && !connectedToHost && hostId != null && !connecting)
                {
                    connectTo(hostId, "HOST");
                }
                else if (running && mGoogleApiClient.isConnected() && !connectedToHost && !discovering && !connecting)
                {
                    startDiscovery();
                }

            }
        }, 1000, 500, TimeUnit.MILLISECONDS);
    }

    protected void startDiscovery() {
        if (!isConnectedToNetwork()) {
            // Implement logic when device is not connected to a network
            Log.e("MAIN", "NOT CONNECTED");
            return;
        }

        discovering = true;
        String serviceId = "com.example.thomas.mastermario";

        // Set an appropriate timeout length in milliseconds
        long DISCOVER_TIMEOUT = 30 * 1000L;
        scheduler.schedule(new Runnable()
        {
            @Override
            public void run()
            {
                discovering = false;
            }
        }, DISCOVER_TIMEOUT, TimeUnit.MILLISECONDS);
        // Discover nearby apps that are advertising with the required service ID.
        Nearby.Connections.startDiscovery(mGoogleApiClient, serviceId, DISCOVER_TIMEOUT, this)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            // Device is discovering
                            Log.d("MAIN","Discovering...");
                        } else {
                            int statusCode = status.getStatusCode();
                            // Advertising failed - see statusCode for more details
                            Log.d("MAIN","Discovering failed: " + status.getStatusMessage());
                            discovering = false;
                        }
                    }
                });
    }

    public void setOnConnectedListener(OnConnectedListener onConnectedListener)
    {
        this.onConnectedListener = onConnectedListener;
    }

    protected void connectTo(String endpointId, final String endpointName) {
        connecting = true;
        Log.d("Client", "Connecting to " + endpointId);
        // Send a connection request to a remote endpoint. By passing 'null' for
        // the name, the Nearby Connections API will construct a default name
        // based on device model such as 'LGE Nexus 5'.
        String myName = null;
        byte[] myPayload = null;
        Nearby.Connections.sendConnectionRequest(mGoogleApiClient, myName,
                endpointId, myPayload, new Connections.ConnectionResponseCallback() {
                    @Override
                    public void onConnectionResponse(String remoteEndpointId, Status status,
                                                     byte[] bytes) {

                        if (status.isSuccess()) {
                            // Successful connection
                            Log.d("MAIN", "Connected to host");
                            connectedToHost = true;
                            if (onConnectedListener != null)
                            {
                                onConnectedListener.connected(true);
                            }

                        } else {
                            // Failed connection
                            Log.d("MAIN", "Connection failed: " + status.toString());
                            connectedToHost = false;
                            discovering = false;
                            hostId = null;
                        }

                        connecting = false;
                    }
                }, this);
    }

    @Override
    public void onEndpointFound(final String endpointId, String deviceId,
                                String serviceId, final String endpointName) {
        // This device is discovering endpoints and has located an advertiser.
        // Write your logic to initiate a connection with the device at
        // the endpoint ID
        Log.d("MAIN", "Endpoint found: " + endpointName);
        hostId = endpointId;
        connectTo(endpointId, endpointName);
    }

    @Override
    public void onEndpointLost(String s)
    {
        Log.d("MAIN", "OnEndPointLost: " + s);
        connectedToHost = false;
        onConnectedListener.connected(false);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle)
    {
        super.onConnected(bundle);
        if (!connectedToHost && hostId == null)
        {
            startDiscovery();
        }

        if (hostId != null)
        {
            Nearby.Connections.sendConnectionRequest(mGoogleApiClient, null,
                    hostId, null, new Connections.ConnectionResponseCallback() {
                        @Override
                        public void onConnectionResponse(String remoteEndpointId, Status status,
                                                         byte[] bytes) {
                            if (status.isSuccess()) {
                                // Successful connection
                                Log.d("MAIN", "Connected to host");
                                connectedToHost = true;
                                hostId = remoteEndpointId;
                            } else {
                                // Failed connection
                                Log.d("MAIN", "Connection failed: " + status.getStatusMessage());
                                connectedToHost = false;
                            }
                        }
                    }, this);
        }
    }

    @Override
    public void onDisconnected(String s)
    {
        Log.d("MAIN", "OnDisconnected: " + s);
        connectedToHost = false;
        onConnectedListener.connected(false);
    }

    public void sendBytesToHost(byte[] bytes)
    {
        if (hostId != null)
        {
            sendBytes(bytes, hostId);
        }
    }
}
