package com.example;

public interface TCPServer
{
    boolean start();
    void stop();
    boolean isRunning();
    void setOnReceiveBytesListener(OnReceiveBytesFromClientListener listener);
    void setOnAcceptListener(OnAcceptListener listener);
    void setOnRemoveClientListener(OnRemoveClientListener listener);
}
