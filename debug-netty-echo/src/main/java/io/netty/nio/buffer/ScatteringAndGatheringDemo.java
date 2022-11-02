package io.netty.nio.buffer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

/**
 * @author lxcecho 909231497@qq.com
 * @since 2021/2/20
 * <p>
 * 前面我们讲的读写操作，都是通过一个Buffer 完成的，NIO 还支持 通过多个Buffer (即 Buffer 数组) 完成读写操作，
 * 即
 * Scattering：将数据写入到 Buffer 时，可以采用 Buffer 数组，依次写入【分散】；
 * Gathering：从 Buffer 读取数据时，可以采用 Buffer 数组，依次读。
 */
public class ScatteringAndGatheringDemo {
    public static void main(String[] args) throws Exception {
        // 使用 ServerSocketChannel 和 SocketChannel 网络
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        InetSocketAddress inetSocketAddress = new InetSocketAddress(7000);

        // 绑定端口到 socket 并启动
        serverSocketChannel.socket().bind(inetSocketAddress);

        // 创建 buffer 数组
        ByteBuffer[] byteBuffers = new ByteBuffer[2];
        byteBuffers[0] = ByteBuffer.allocate(5);
        byteBuffers[1] = ByteBuffer.allocate(3);

        // 等待客户端连接 telnet
        SocketChannel socketChannel = serverSocketChannel.accept();
        int messageLength = 8;// 假定从客户端接收 8个字节
        // 循环读取
        while (true) {
            int byteRead = 0;
            while (byteRead < messageLength) {
                long l = socketChannel.read(byteBuffers);
                byteRead += l;// 累计读取的字节数
                System.out.println("byteRead = " + byteRead);
                // 使用流打印，看看当前的这个 buffer 的 position 和 limit
                Arrays.asList(byteBuffers).stream().map(byteBuffer -> "position=" + byteBuffer.position()
                        + ",limit=" + byteBuffer.limit()).forEach(System.out::println);
            }

            // 将所有的 buffer 进行 flip
            Arrays.asList(byteBuffers).forEach(byteBuffer -> byteBuffer.flip());

            // 将数据读出显示到客户端
            long byteWrite = 0;
            while (byteWrite < messageLength) {
                long l = socketChannel.write(byteBuffers);
                byteWrite += l;
            }

            // 将所有的 buffer 进行 clear
            Arrays.asList(byteBuffers).forEach(byteBuffer -> byteBuffer.clear());
            System.out.println("byteRead=" + byteRead + " byteWrite="
                    + byteWrite + " messageLength=" + messageLength);
        }
    }
}
