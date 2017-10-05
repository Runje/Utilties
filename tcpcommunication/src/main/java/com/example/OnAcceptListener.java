package com.example;

import java.nio.channels.SocketChannel;

/**
 * Created by Thomas on 17.02.2016.
 */
public interface OnAcceptListener
{
    void onAccept(SocketChannel socketChannel);
}
