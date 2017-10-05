package com.example.thomas.nearbyconnections;

/**
 * Created by Thomas on 06.06.2016.
 */
public interface BytesReceivedListener
{
    public void messageReceived(byte[] bytes, String endPointId);
}
