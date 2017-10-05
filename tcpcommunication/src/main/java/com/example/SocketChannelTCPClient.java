package com.example;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Thomas on 20.01.2016.
 */
public class SocketChannelTCPClient extends TCPClient
{
    private ExecutorService service;
    private SocketChannel socketChannel;
    private Selector selector;
    private boolean connectionPending;

    public SocketChannelTCPClient()
    {
        this(0, "");
    }

    public SocketChannelTCPClient(int port, String ipaddress)
    {
        super(port, ipaddress);
    }

    @Override
    protected void tryConnect()
    {
        if (isConnected() || connectionPending)
        {
            return;
        }

        connectionPending = true;
        try
        {
            socketChannel = SocketChannel.open();
            selector = Selector.open();
            service = Executors.newCachedThreadPool();
             service.submit(new Runnable()
            {
                @Override
                public void run()
                {

                    boolean connected = false;
                    try
                    {
                        connected = socketChannel.connect(new InetSocketAddress(ipaddress, port));

                        socketChannel.configureBlocking(false);
                        socketChannel.register(selector, SelectionKey.OP_READ);
                        connectionPending = false;

                        service.submit(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                handleSelection();
                            }
                        });

                        setConnected(connected);
                    } catch (IOException e)
                    {
                        e.printStackTrace();
                        disconnect();
                        connectionPending = false;
                    }
                }
            });

        } catch (IOException e)
        {
            e.printStackTrace();
            disconnect();
        }
    }

    private void handleSelection()
    {
        ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
        ByteBuffer contentBuffer = ByteBuffer.allocate(bufferSize);
        boolean lengthRead = false;
        int length = 0;
        while (true)
        {
            try
            {
                int selected = selector.select();
                if (selected == 0)
                {
                    System.out.println("ZERO Selected");
                    continue;
                }

                System.out.println("Selected: " + selected);
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                while (keyIterator.hasNext())
                {
                    SelectionKey selectionKey = keyIterator.next();
                    keyIterator.remove();
                    if (selectionKey.isConnectable())
                    {
                        System.out.println("Connection pending: " + socketChannel.isConnectionPending());
                        /**if (socketChannel.isConnectionPending())
                        {
                            while (!socketChannel.finishConnect())
                            {
                                System.out.println("Waiting");
                                Thread.sleep(100);
                            }
                        }
                        System.out.println("Connected!");
                        setConnected(true);**/

                    }
                    if (selectionKey.isReadable())
                    {
                        System.out.println("Read");
                        int bytesRead = 0;
                        if (!lengthRead)
                        {
                            lengthBuffer.clear();
                            while(bytesRead < 4)
                            {
                                bytesRead += socketChannel.read(lengthBuffer);
                                if (bytesRead <= 0)
                                {
                                    disconnect();
                                    break;
                                }
                            }

                            lengthBuffer.flip();
                            length = lengthBuffer.getInt();
                            System.out.println("Length: " + length);
                            lengthRead = true;
                        }
                        else
                        {
                            contentBuffer.clear();
                            if (length - 4 > bufferSize)
                            {
                                System.out.println("Buffer size too small");
                                throw new RuntimeException("Buffer size too small! Content length: " + (length - 4) + ", bufferSize: " + bufferSize);
                            }

                            contentBuffer.limit(length - 4);
                            while(bytesRead < length - 4)
                            {
                                bytesRead += socketChannel.read(contentBuffer);
                                if (bytesRead <= 0)
                                {
                                    setConnected(false);
                                    break;
                                }

                                System.out.println("Bytes read: " + bytesRead);
                            }


                            contentBuffer.flip();
                            final byte[] bytes = new byte[length - 4];
                            contentBuffer.get(bytes);
                            service.submit(new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    receiveBytes(bytes);
                                }
                            });
                            lengthRead = false;
                            System.out.println("All Bytes read: " + (length - 4));
                            length = 0;
                        }

                        if (!isConnected())
                        {
                            break;
                        }
                    }
                    if (selectionKey.isWritable())
                    {
                        System.out.println("Write");
                    }
                }
            } catch (IOException e)
            {
                e.printStackTrace();
                disconnect();
                break;
            }
            catch(ClosedSelectorException e)
            {
                break;
            }
        }

        System.out.println("Selector thread ends");
    }

    @Override
    public void disconnect()
    {
        System.out.println("Disconnecting");
        if (socketChannel != null)
        {
            try
            {
                socketChannel.close();
                if (selector != null)
                {
                    selector.close();
                }

                if (service != null)
                {
                    service.shutdown();
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        setConnected(false);
    }

    @Override
    public void sendMessage(final Message msg)
    {
        System.out.println("Trying to send Message: " + msg.toString());
        if (socketChannel == null || !isConnected())
        {
            System.out.println("Cannot send message: Connected: " + isConnected() + ", null: " + socketChannel == null);
            return;
        }

        service.submit(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    System.out.println("Runnable...");
                    ByteBuffer buffer = msg.getBuffer();
                    int bytes = 0;
                    while(buffer.hasRemaining())
                    {
                        bytes += socketChannel.write(buffer);
                    }

                    System.out.println("Wrote bytes: " + bytes + "/" + buffer.limit());
                } catch (IOException e)
                {
                    e.printStackTrace();
                    disconnect();
                }
            }
        });
    }
}
