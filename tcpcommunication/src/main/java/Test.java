import com.example.Message;
import com.example.TCPClient;
import com.example.OnConnectionChangedListener;
import com.example.SocketChannelTCPClient;

import java.nio.ByteBuffer;

/**
 * Created by Thomas on 16.01.2016.
 */
public class Test
{
    public static void main(String[] args)
    {
        System.out.println("Start");
        TCPClient tcpClient = new SocketChannelTCPClient(4400, "127.0.0.1");
        tcpClient.addOnConnectionChangedListener(new OnConnectionChangedListener()
        {
            @Override
            public void onConnectionChanged(boolean connected)
            {
                System.out.println("Connection changed: " + connected);
            }
        });
        tcpClient.connect();

        Message msg = new Message()
        {
            @Override
            public ByteBuffer getBuffer()
            {
                ByteBuffer buffer = ByteBuffer.allocate(4);
                buffer.putInt(10);
                return buffer;
            }
        };

        System.out.println("Waiting for connection...");
        while(!tcpClient.isConnected())
        {
            // wait
        }
        System.out.println("Connected!");
        tcpClient.sendMessage(msg);

        //while(true)
        {
            try
            {
                Thread.sleep(5000);
                //tcpClient.disconnect();
                //tcpClient = new SocketChannelTCPClient(4400, "127.0.0.1");
                //tcpClient.connect();
                //tcpClient.sendMessage(msg);
                //break;
            } catch (InterruptedException e)
            {
                e.printStackTrace();
                //break;
            }
        }
        tcpClient.disconnect();
        System.out.println("End");
    }
}
