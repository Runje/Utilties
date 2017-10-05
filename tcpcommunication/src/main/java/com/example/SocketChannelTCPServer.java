package com.example;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Thomas on 16.01.2016.
 */
public class SocketChannelTCPServer implements TCPServer
{
    public static int bufferSize = 9000;
    private final int port;
    private final ExecutorService service;
    private Selector selector;
    protected OnReceiveBytesFromClientListener onReceiveBytesListener;
    private boolean running;
    private OnAcceptListener onAcceptListener;


    @Override
    public void setOnRemoveClientListener(OnRemoveClientListener onRemoveClientListener)
    {
        this.onRemoveClientListener = onRemoveClientListener;
    }

    private OnRemoveClientListener onRemoveClientListener;

    @Override
    public void setOnReceiveBytesListener(OnReceiveBytesFromClientListener onReceiveBytesListener)
    {
        this.onReceiveBytesListener = onReceiveBytesListener;
    }

    @Override
    public void setOnAcceptListener(OnAcceptListener listener)
    {
        onAcceptListener = listener;
    }

    public SocketChannelTCPServer(int port)
    {
        this.port = port;
        service = Executors.newCachedThreadPool();
    }

    @Override
    public boolean start()
    {
        try
        {
            running = true;
            selector = Selector.open();
            ServerSocketChannel socketChannel = ServerSocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.bind(new InetSocketAddress(port));
            socketChannel.register(selector, SelectionKey.OP_ACCEPT);
            handleSelector();
            return true;
        } catch (IOException e)
        {
            e.printStackTrace();
            running = false;
            return false;
        }
    }

    private void handleSelector()
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                List<Client> clients = new ArrayList<>();
                while(running)
                {
                    try
                    {
                        int selected = selector.select();
                        System.out.println("selected: " + selected);
                        Iterator it = selector.selectedKeys().iterator();
                        while (it.hasNext())
                        {
                            SelectionKey selKey = (SelectionKey) it.next();
                            it.remove();

                            if (selKey.isAcceptable())
                            {
                                ServerSocketChannel ssChannel = (ServerSocketChannel) selKey.channel();
                                SocketChannel sc = ssChannel.accept();
                                sc.configureBlocking(false);
                                clients.add(new Client(sc));
                                System.out.println("Accepted Client: " + clients.size());
                                sc.register(selector, SelectionKey.OP_READ);
                                if (onAcceptListener != null)
                                {
                                    onAcceptListener.onAccept(sc);
                                }

                            }

                            if (selKey.isReadable())
                            {
                                System.out.println("Reading...");
                                SocketChannel channel = (SocketChannel) selKey.channel();
                                final Client client = getClient(channel, clients);
                                if (client != null)
                                {
                                    try
                                    {
                                        int bytesRead = 0;
                                        if (!client.isLengthRead)
                                        {
                                            client.lengthBuffer.clear();
                                            while (bytesRead < 4)
                                            {
                                                bytesRead += channel.read(client.lengthBuffer);
                                                if (bytesRead <= 0)
                                                {
                                                    closeClient(client, clients);
                                                    break;
                                                }
                                            }

                                            client.lengthBuffer.flip();
                                            client.length = client.lengthBuffer.getInt();
                                            System.out.println("Length: " + client.length);
                                            client.isLengthRead = true;
                                        } else
                                        {
                                            client.contentBuffer.clear();
                                            client.contentBuffer.limit(client.length - 4);
                                            while (bytesRead < client.length)
                                            {
                                                bytesRead += channel.read(client.contentBuffer);
                                                if (bytesRead <= 0)
                                                {
                                                    closeClient(client, clients);
                                                    break;
                                                }

                                                client.contentBuffer.flip();
                                                final byte[] bytes = new byte[client.length - 4];
                                                client.contentBuffer.get(bytes);
                                                service.submit(new Runnable()
                                                {
                                                    @Override
                                                    public void run()
                                                    {
                                                        System.out.println("Calling receiveBytes");
                                                        onReceiveBytes(bytes, client.channel);
                                                    }
                                                });
                                                client.isLengthRead = false;
                                                System.out.println("Bytes read: " + (client.length - 4));
                                                client.length = 0;
                                            }
                                        }
                                    }
                                    catch (Exception e)
                                    {
                                        e.printStackTrace();
                                        closeClient(client, clients);
                                    }
                                 }
                                else
                                {
                                    System.out.println("No client found");
                                }
                            }
                        }
                    } catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }

                //System.out.println("Thread ends");
            }
        }).start();
    }

    private void closeClient(Client client, List<Client> clients)
    {
        try
        {
            client.channel.close();
        } catch (IOException e)
        {
            e.printStackTrace();
        }

        clients.remove(client);
        System.out.println("Remove client, Clients left: " + clients.size());

        if (onRemoveClientListener != null)
        {
            onRemoveClientListener.onRemoveClient(client.channel);
        }
    }

    private void onReceiveBytes(byte[] bytes, SocketChannel channel)
    {
        if (onReceiveBytesListener != null)
        {
            onReceiveBytesListener.onReceiveBytes(bytes, channel);
        }
    }

    private Client getClient(SocketChannel channel, List<Client> clients)
    {
        for (Client client : clients)
        {
            if (client.channel == channel)
            {
                return client;
            }
        }
        
        return null;
    }

    public synchronized boolean sendBytes(ByteBuffer buffer, SocketChannel channel)
    {
        try
        {
            System.out.println("Trying to write " + buffer.limit() + " bytes. Channel: " + channel);
            int bytes = 0;
            while(buffer.hasRemaining())
            {
                bytes += channel.write(buffer);
            }
            System.out.println("Wrote bytes: " + bytes + "/" + buffer.limit());
            return true;
        } catch (IOException e)
        {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void stop()
    {
        running = false;
    }

    @Override
    public boolean isRunning()
    {
        return running;
    }

    private static class Client
    {
        SocketChannel channel;
        public boolean isLengthRead = false;
        public int length = 0;
        public ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
        public ByteBuffer contentBuffer = ByteBuffer.allocate(bufferSize);

        public Client(SocketChannel channel)
        {
            this.channel = channel;
        }
    }
}
