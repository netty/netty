package io.netty.nio.channel;

import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * @author lxcecho 909231497@qq.com
 * @since 10.09.2021
 */
public class ServerSocketChannelTest {

    public static final String GREETING = "Hello java nio.\r\n";

    @Test
    public void buffer02() throws Exception {
        int port = 9999; // default

        ByteBuffer buffer = ByteBuffer.wrap(GREETING.getBytes());

        // 打开 ServerSocketChannel
        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.socket().bind(new InetSocketAddress(port));
        // ServerSocketChannel 设置为非阻塞模式
        ssc.configureBlocking(false);

        // 监听所有新连接传入
        while (true) {
            System.out.println("waiting for connections...");
            // 监听新进的连接，返回一个包含新进的连接 SocketChannel，因此 accept 方法会一直阻塞到有新的连接到达，所以这里会阻塞住进程，一直等待新的连接
            // 通常不会仅仅只监听一个连接，在 while 循环中调用
            SocketChannel sc = ssc.accept();
            // 在非阻塞模式下，accept 会立即返回，如果还没有新进来的连接，返回的将是 null
            if (sc == null) {
                System.out.println("null");
                Thread.sleep(2000);
            } else {
                System.out.println("incoming connection from : " + sc.socket().getRemoteSocketAddress());
                // 指针0
                buffer.rewind();
                sc.write(buffer);
                sc.close();
            }
        }

    }

}
