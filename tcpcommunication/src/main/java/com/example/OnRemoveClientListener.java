package com.example;

import java.nio.channels.SocketChannel;

/**
 * Created by Thomas on 21.02.2016.
 */
public interface OnRemoveClientListener
{
    void onRemoveClient(SocketChannel channel);
}
