package io.netty.nio.channel;

import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * @author lxcecho 909231497@qq.com
 * @since 10.09.2021
 */
public class SocketChannelTest {

    @Test
    public void channel() throws Exception{

        // 创建 SocketChannel 方式一
        SocketChannel socketChannel = SocketChannel.open(
                new InetSocketAddress("www.baidu.com", 80)
        );

        // 创建 SocketChannel 方式二
//        SocketChannel socketChannel2 = SocketChannel.open();
//        socketChannel2.connect(new InetSocketAddress("www.baidu.com", 80));

//        socketChannel.isOpen(); // 测试  SocketChannel 是否为 open 状态
//        socketChannel.isConnected(); // 测试 SocketChannel 是否已经被连接
//        socketChannel.isConnectionPending(); // 测试 SocketChannel 是否正在进行连接
//        socketChannel.finishConnect(); // 校验正在进行套接字连接的 SocketChannel 是否已经完成连接

        // 设置和获取 socket 套接字的相关参数
//        socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, Boolean.TRUE)
//                .setOption(StandardSocketOptions.TCP_NODELAY, Boolean.TRUE);
//        socketChannel.getOption(StandardSocketOptions.TCP_NODELAY);


//        // 设置 SocketChannel 的读写模式：false：非阻塞，true：阻塞
        socketChannel.configureBlocking(false);

        ByteBuffer buffer = ByteBuffer.allocate(16);
        // 阻塞式读，执行到这里线程将阻塞，控制台不会打印 read over...
        // 非阻塞式读，控制台会打印 read over...
        socketChannel.read(buffer);
        socketChannel.close();
        System.out.println("read over...");

    }

}
