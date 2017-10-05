package com.example;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by Thomas on 16.01.2016.
 * Cannot used with Android, because AsynchronousSocketChannel is not supported by android!
 */
public class FutureTCPClient extends TCPClient
{
    private ExecutorService service;
    private AsynchronousSocketChannel channel;

    public FutureTCPClient()
    {
    }

    public FutureTCPClient(int port, String ipaddress)
    {
        this.port = port;
        this.ipaddress = ipaddress;
    }

    @Override
    protected void tryConnect()
    {
        try
        {
            channel = AsynchronousSocketChannel.open();
            SocketAddress socketAddress = new InetSocketAddress(ipaddress, port);
            channel.connect(socketAddress, channel, new CompletionHandler<Void, AsynchronousSocketChannel>()
            {
                @Override
                public void completed(Void result, AsynchronousSocketChannel channel)
                {
                    setConnected(true);
                    System.out.println("Connected");
                    ByteBuffer buffer = ByteBuffer.allocate(9000);
                    ReadAttachment attachment = new ReadAttachment(buffer, channel);
                    service = Executors.newCachedThreadPool();
                    channel.read(buffer, attachment, new ReadHandler());
                }

                @Override
                public void failed(Throwable exc, AsynchronousSocketChannel attachment)
                {
                    setConnected(false);
                    System.out.println("Connecting failed: " + exc.getMessage());
                }
            });
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private class ReadAttachment
    {
        boolean lengthRead;
        ByteBuffer buffer;
        AsynchronousSocketChannel channel;
        public int length;
        public int bufferPos;

        public ReadAttachment(ByteBuffer buffer, AsynchronousSocketChannel channel)
        {
            this.buffer = buffer;
            this.channel = channel;
        }
    }

    private class ReadHandler implements CompletionHandler<Integer, ReadAttachment>
    {
        @Override
        public void completed(Integer result, ReadAttachment attachment)
        {
            System.out.println("Read bytes: " + result);

            ByteBuffer buffer = attachment.buffer;
            int availableBytes = buffer.position();
            int bufferLength = availableBytes;
            buffer.flip();
            buffer.position(attachment.bufferPos);
            while(availableBytes > 0)
            {
                if (!attachment.lengthRead)
                {
                    // read length
                    if (availableBytes >= 4)
                    {
                        // read length - 4 because length is counted
                        attachment.length = buffer.getInt() - 4;
                        attachment.lengthRead = true;
                        availableBytes -= 4;
                        System.out.println("Read length: " + attachment.length);
                    }
                    else
                    {
                        buffer.position(bufferLength);
                        attachment.bufferPos = bufferLength - availableBytes;
                        break;
                    }
                }
                else
                {
                    // read bytes
                    if (availableBytes >= attachment.length)
                    {
                        final byte[] receivedBytes = new byte[attachment.length];
                        buffer.get(receivedBytes);
                        // run in background thread to start reading again
                        service.submit(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                receiveBytes(receivedBytes);
                            }
                        });

                        System.out.println("Read Message: " + attachment.length);
                        availableBytes -= attachment.length;
                        attachment.length = 0;
                        attachment.lengthRead = false;
                    } else
                    {
                        buffer.position(bufferLength);
                        attachment.bufferPos = bufferLength - availableBytes;
                        break;
                    }
                }
            }

            if (availableBytes == 0)
            {
                buffer.clear();
                attachment.bufferPos = 0;
            }

            attachment.channel.read(buffer, attachment, this);
        }

        @Override
        public void failed(Throwable exc, ReadAttachment attachment)
        {
            System.out.println("Failed");
            setConnected(false);
        }
    }

    private String byteToString(ByteBuffer buffer, int i)
    {
        byte[] bytes = new byte[i];
        buffer.get(bytes);
        return (new String(bytes, Charset.forName("UTF8"))).trim();
    }

    public void disconnect()
    {
        if (channel != null)
        {
            try
            {
                channel.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    // synchronous
    public void sendMessage(Message msg)
    {
        if (connected)
        {
            Future<Integer> f = channel.write(msg.getBuffer());
            try
            {
                int bytesWritten = f.get();
                System.out.println("Bytes written: " + bytesWritten);
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            } catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }
    }
}
