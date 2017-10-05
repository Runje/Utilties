package com.example;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by Thomas on 16.01.2016.
 */
public abstract class TCPClient
{
    protected boolean connected;
    protected int port;
    protected String ipaddress;
    protected int bufferSize = 9000;
    protected List<OnReceiveBytesListener> onReceiveBytesListeners = new ArrayList<>();
    protected List<OnConnectionChangedListener> onConnectionChangedListeners = new ArrayList<>();
    protected List<MessageReceivedListener> messageReceivedListeners = new ArrayList<>();

    public void addOnConnectionChangedListener(OnConnectionChangedListener onConnectionChangedListener)
    {
        this.onConnectionChangedListeners.add(onConnectionChangedListener);
        System.out.println("OnConnectionChangedListeners: " + onConnectionChangedListeners.size());
    }

    public void removeOnConnectionChangedListener(OnConnectionChangedListener onConnectionChangedListener)
    {
        this.onConnectionChangedListeners.remove(onConnectionChangedListener);
        System.out.println("OnConnectionChangedListeners: " + onConnectionChangedListeners.size());
    }

    public int getPort()
    {
        return port;
    }

    public void setPort(int port)
    {
        this.port = port;
    }

    public String getIpaddress()
    {
        return ipaddress;
    }

    public void setIpaddress(String ipaddress)
    {
        this.ipaddress = ipaddress;
    }

    public int getBufferSize()
    {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize)
    {
        this.bufferSize = bufferSize;
    }

    public void addMessageReceivedListener(MessageReceivedListener messageReceivedListener)
    {
        this.messageReceivedListeners.add(messageReceivedListener);
    }
    public boolean isConnected()
    {
        return connected;
    }

    public TCPClient()
    {
    }

    public TCPClient(int port, String ipaddress)
    {
        this.port = port;
        this.ipaddress = ipaddress;
    }

    public void connect(String ipaddress, int port)
    {
        if (ipaddress == null || port > Network.MAX_PORT || port < Network.MIN_PORT || connected)
        {
            return;
        }

        this.ipaddress = ipaddress;
        this.port = port;
        connect();
    }

    public void connect()
    {
        if (ipaddress == null || port > Network.MAX_PORT || port < Network.MIN_PORT || connected)
        {
            return;
        }

        tryConnect();
    }

    protected abstract void tryConnect();

    protected void setConnected(boolean connected)
    {
        if (this.connected != connected)
        {
            this.connected = connected;
            List<OnConnectionChangedListener> remove = new ArrayList<>();
            for(OnConnectionChangedListener onConnectionChangedListener:onConnectionChangedListeners)
            {
                if (onConnectionChangedListener == null)
                {
                    System.out.println("NULL Listener detected");
                    remove.add(onConnectionChangedListener);
                }

                onConnectionChangedListener.onConnectionChanged(connected);
            }

            for(OnConnectionChangedListener listener:remove)
            {
                onConnectionChangedListeners.remove(listener);
            }
        }
    }

    protected void receiveBytes(byte[] receivedBytes)
    {
        for(OnReceiveBytesListener onReceiveBytesListener : onReceiveBytesListeners)
        {
            onReceiveBytesListener.onReceiveBytes(receivedBytes);
        }
    }

    public void addOnReceiveBytesListener(OnReceiveBytesListener onReceiveBytesListener)
    {
        this.onReceiveBytesListeners.add(onReceiveBytesListener);
    }

    public abstract void disconnect();

    public abstract void sendMessage(Message msg);
}
