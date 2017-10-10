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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Thomas on 16.01.2016.
 */
public class SocketChannelTCPServer implements TCPServer
{
    private Logger logger = LoggerFactory.getLogger(getClass().getSimpleName());
    public static int bufferSize = 9000;
    private final int port;
    private final ExecutorService service;
    private Selector selector;
    protected OnReceiveBytesFromClientListener onReceiveBytesListener;
    private boolean running;
    private OnAcceptListener onAcceptListener;
    private Thread receiveThread;
    private ServerSocketChannel socketChannel;


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
            socketChannel = ServerSocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.bind(new InetSocketAddress(port));
            socketChannel.register(selector, SelectionKey.OP_ACCEPT);
            handleSelector();
            return true;
        } catch (IOException e)
        {
            logger.error(e.getMessage());
            running = false;
            return false;
        }
    }

    private void handleSelector()
    {
        receiveThread = new Thread(new Runnable()
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
                        logger.info("selected: " + selected);
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
                                logger.info("Accepted Client: " + clients.size());
                                sc.register(selector, SelectionKey.OP_READ);
                                if (onAcceptListener != null)
                                {
                                    onAcceptListener.onAccept(sc);
                                }

                            }

                            if (selKey.isReadable())
                            {
                                logger.info("Reading...");
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
                                            logger.info("Length: " + client.length);
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
                                                        logger.info("Calling receiveBytes");
                                                        onReceiveBytes(bytes, client.channel);
                                                    }
                                                });
                                                client.isLengthRead = false;
                                                logger.info("Bytes read: " + (client.length - 4));
                                                client.length = 0;
                                            }
                                        }
                                    }
                                    catch (Exception e)
                                    {
                                        logger.error(e.getMessage());
                                        closeClient(client, clients);
                                    }
                                 }
                                else
                                {
                                    logger.info("No client found");
                                }
                            }
                        }
                    } catch (IOException e)
                    {
                        logger.error(e.getMessage());
                    }
                }

                logger.info("Receiver Thread ends");
            }
        });
        receiveThread.start();
    }

    private void closeClient(Client client, List<Client> clients)
    {
        try
        {
            client.channel.close();
        } catch (IOException e)
        {
            logger.error(e.getMessage());
        }

        clients.remove(client);
        logger.info("Remove client, Clients left: " + clients.size());

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
            logger.info("Trying to write " + buffer.limit() + " bytes. Channel: " + channel);
            int bytes = 0;
            while(buffer.hasRemaining())
            {
                bytes += channel.write(buffer);
            }
            logger.info("Wrote bytes: " + bytes + "/" + buffer.limit());
            return true;
        } catch (IOException e)
        {
            logger.error(e.getMessage());
            return false;
        }
    }

    @Override
    public void stop()
    {
        running = false;
        try
        {
            logger.info("Closing selector...");
            selector.close();
        } catch (IOException e) {
            logger.error(e.getMessage());
        }

        if (socketChannel != null) {
            try {
                logger.info("Closing socketchannel...");
                socketChannel.close();
                logger.info("Closed socket channel");
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        }

        if (receiveThread != null) {
            try {
                logger.info("Waiting for receive thread to end...");
                receiveThread.join();

            } catch (InterruptedException e) {
                logger.error(e.getMessage());
            }
        }

        logger.info("SocketChannelTCPServer closed");

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
