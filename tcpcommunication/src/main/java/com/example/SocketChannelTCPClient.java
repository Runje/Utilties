package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private Logger logger = LoggerFactory.getLogger(getClass().getSimpleName());
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
             service.submit(() -> {

                 boolean connected = false;
                 try
                 {
                     connected = socketChannel.connect(new InetSocketAddress(ipaddress, port));

                     socketChannel.configureBlocking(false);
                     socketChannel.register(selector, SelectionKey.OP_READ);
                     connectionPending = false;

                     service.submit(() -> handleSelection());

                     setConnected(connected);
                 } catch (IOException e)
                 {
                     logger.error(e.getMessage());
                     disconnect();
                     connectionPending = false;
                 }
             });

        } catch (IOException e)
        {
            logger.error(e.getMessage());
            disconnect();
        }
    }

    private void handleSelection()
    {
        ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
        //ByteBuffer contentBuffer = ByteBuffer.allocate(bufferSize);
        boolean lengthRead = false;
        int length = 0;
        while (true)
        {
            try
            {
                int selected = selector.select();
                if (selected == 0)
                {
                    logger.trace("ZERO Selected");
                    continue;
                }

                logger.trace("Selected: " + selected);
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                while (keyIterator.hasNext())
                {
                    SelectionKey selectionKey = keyIterator.next();
                    keyIterator.remove();
                    if (selectionKey.isConnectable())
                    {
                        logger.trace("Connection pending: " + socketChannel.isConnectionPending());
                        /**if (socketChannel.isConnectionPending())
                        {
                            while (!socketChannel.finishConnect())
                            {
                                logger.trace("Waiting");
                                Thread.sleep(100);
                            }
                        }
                        logger.trace("Connected!");
                        setConnected(true);**/

                    }
                    if (selectionKey.isReadable())
                    {
                        logger.trace("Read");
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
                            logger.trace("Length: " + length);
                            lengthRead = true;
                        }
                        else
                        {
                            /**contentBuffer.clear();
                            if (length - 4 > bufferSize)
                            {
                                logger.trace("Buffer size too small");
                                throw new RuntimeException("Buffer size too small! Content length: " + (length - 4) + ", bufferSize: " + bufferSize);
                            }*/

                            ByteBuffer contentBuffer = ByteBuffer.allocate(length - 4);
                            while(bytesRead < length - 4)
                            {
                                bytesRead += socketChannel.read(contentBuffer);
                                if (bytesRead <= 0)
                                {
                                    setConnected(false);
                                    break;
                                }

                                logger.trace("Bytes read: " + bytesRead);
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
                            logger.trace("All Bytes read: " + (length - 4));
                            length = 0;
                        }

                        if (!isConnected())
                        {
                            break;
                        }
                    }
                    if (selectionKey.isWritable())
                    {
                        logger.trace("Write");
                    }
                }
            } catch (IOException e)
            {
                logger.error(e.getMessage());
                disconnect();
                break;
            }
            catch(ClosedSelectorException e)
            {
                break;
            }
        }

        logger.trace("Selector thread ends");
    }

    @Override
    public void disconnect()
    {
        logger.trace("Disconnecting");
        connectionPending = false;
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
                logger.error(e.getMessage());
            }
        }

        setConnected(false);
    }

    @Override
    public void sendMessage(final Message msg)
    {
        logger.trace("Trying to send Message: " + msg.toString());
        if (socketChannel == null || !isConnected())
        {
            logger.trace("Cannot send message: Connected: " + isConnected() + ", null: " + (socketChannel == null));
            return;
        }

        service.submit(() -> {
            try
            {
                logger.trace("Runnable...");
                ByteBuffer buffer = msg.getBuffer();
                int bytes = 0;
                while(buffer.hasRemaining())
                {
                    bytes += socketChannel.write(buffer);
                }

                logger.info("Wrote bytes: " + bytes + "/" + buffer.limit());
            } catch (IOException e)
            {
                logger.error(e.getMessage());
                disconnect();
            }
        });
    }
}
