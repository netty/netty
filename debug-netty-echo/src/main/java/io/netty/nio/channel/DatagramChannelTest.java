package io.netty.nio.channel;

import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @author lxcecho 909231497@qq.com
 * @since 11.09.2021
 */
public class DatagramChannelTest {

    public void channel() throws Exception {
        DatagramChannel datagramChannel = DatagramChannel.open();
        // 打开 10086 端口接收 UDP 数据包
        datagramChannel.socket().bind(new InetSocketAddress(10086));

        // 接收数据
//        ByteBuffer receiveBuffer = ByteBuffer.allocate(64);
//        receiveBuffer.clear();
//        // SocketAddress 可以获得发包的 Ip 端口等信息，用 toString 查看
//        SocketAddress receiveAddr = datagramChannel.receive(receiveBuffer);

        // 发送数据
//        ByteBuffer sendBuffer = ByteBuffer.wrap("client send : ".getBytes());
//        datagramChannel.send(sendBuffer, new InetSocketAddress("127.0.0.1", 10086));

        // UDP 不存在真正意义上的连接，这里的连接是向特定服务地址用 read 和 write 接收发送数据包
        // read 和 write 只有在 connect 后才能使用，不然会抛 NotYetConnectedException 异常，用 read 接收时，如果没有接收到包，会抛 PortUnreachableException 异常
//        datagramChannel.connect(new InetSocketAddress("127.0.0.1", 10086));
//        int read = datagramChannel.read(sendBuffer);
//        datagramChannel.write(sendBuffer);

    }


    /**
     * 发包的 datagram
     *
     * @throws Exception
     */
    @Test
    public void sendDatagram() throws Exception {
        DatagramChannel sendChannel = DatagramChannel.open();
        InetSocketAddress sendAddress = new InetSocketAddress("127.0.0.1", 10086);
        while (true) {
            sendChannel.send(ByteBuffer.wrap("send datagram".getBytes("UTF-8")), sendAddress);
            System.out.println("client send datagram ...");
            Thread.sleep(1000);
        }
    }

    /**
     * 收包的 datagram
     *
     * @throws Exception
     */
    @Test
    public void receiveDatagram() throws Exception {
        DatagramChannel receiveChannel = DatagramChannel.open();
        InetSocketAddress receiveAddress = new InetSocketAddress("127.0.0.1", 10086);
        // 绑定
        receiveChannel.bind(receiveAddress);
        ByteBuffer receiveBuffer = ByteBuffer.allocate(512);

        // 接收
        while (true) {
            receiveBuffer.clear();
            SocketAddress sendAddress = receiveChannel.receive(receiveBuffer);
            receiveBuffer.flip();
            System.out.println(sendAddress.toString());
            System.out.println(Charset.forName("UTF-8").decode(receiveBuffer));
        }
    }

    /**
     * 连接 read 和 write
     *
     * @throws Exception
     */
    @Test
    public void testConn() throws Exception {
        DatagramChannel connChannel = DatagramChannel.open();
        // 绑定
        connChannel.bind(new InetSocketAddress(10086));
        // 连接
        connChannel.connect(new InetSocketAddress("127.0.0.1", 10086));
        // write 方法
        connChannel.write(ByteBuffer.wrap("send datagram".getBytes(StandardCharsets.UTF_8)));
        ByteBuffer readBuffer = ByteBuffer.allocate(512);
        while (true) {
            try {
                readBuffer.clear();
                connChannel.read(readBuffer);
                readBuffer.flip();
                System.out.println(Charset.forName("UTF-8").decode(readBuffer));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
