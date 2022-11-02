package io.netty.nio.selector;

import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * @author lxcecho 909231497@qq.com
 * @since 10.09.2021
 */
public class SelectorTest {
    @Test
    public void server() throws Exception {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress("127.0.0.1", 9090));
        serverSocketChannel.configureBlocking(false);

        Selector selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        ByteBuffer readBuffer = ByteBuffer.allocate(1024);
        ByteBuffer writeBuffer = ByteBuffer.allocate(1024);

        writeBuffer.put("received".getBytes());
        writeBuffer.flip();

        while(true){
            int nReady = selector.select();
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            while(iterator.hasNext()){
                SelectionKey selectionKey = iterator.next();
                iterator.remove();

                if(selectionKey.isAcceptable()) {
                    // 创建新的连接，并且把连接注册到 selector 上，而且，声明这个 channel 只对读操作感兴趣
                    SocketChannel socketChannel = serverSocketChannel.accept();
                    socketChannel.configureBlocking(false);
                    socketChannel.register(selector, SelectionKey.OP_READ);
                } else if(selectionKey.isReadable()){
                    SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                    readBuffer.clear();
                    socketChannel.read(readBuffer);

                    readBuffer.flip();
                    System.out.println("received : "+ new String(readBuffer.array()));
                    selectionKey.interestOps(SelectionKey.OP_WRITE);
                } else if(selectionKey.isWritable()){
                    writeBuffer.rewind();
                    SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                    socketChannel.write(writeBuffer);
                    selectionKey.interestOps(SelectionKey.OP_READ);
                }
            }
        }

    }

    @Test
    public void client() throws Exception {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.connect(new InetSocketAddress("127.0.0.1", 9090));

        ByteBuffer readBuffer = ByteBuffer.allocate(32);
        ByteBuffer writeBuffer = ByteBuffer.allocate(32);

        writeBuffer.put("hello".getBytes());
        writeBuffer.flip();

        while (true){
            writeBuffer.rewind();
            socketChannel.write(writeBuffer);
            readBuffer.clear();
            socketChannel.read(readBuffer);
        }

    }


    @Test
    public void testSelector() throws Exception {
        // 1 获取 Selector 选择器
        Selector selector = Selector.open();

        // 2 获取通道
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();

        // 3 设为 非阻塞
        /**
         * 与 Selector 一起使用时，Channel 必须处于非阻塞模式下，否则将抛出异常 IllegalBlockingModeException。
         * 这意味着，FileChannel 不能与 Selector 一起使用，因为 FileChannel 不能切换到非阻塞模式，而套接字相关的所有通道都可以。
         */
        serverSocketChannel.configureBlocking((false));

        // 4 绑定链接
        serverSocketChannel.bind(new InetSocketAddress(8090));

        /**
         * 一个通道，并没有一定要支持所有的四种操作。比如服务器通道 ServerSocketChannel 支持 Accept 接收操作，而 SocketChannel 客户端则不支持。
         * 可以通过通道上的 validOps() 方法，来获取特定通道下所有支持的操作集合。
         */
        System.out.println(serverSocketChannel.validOps());

        // 5 将通道注册到选择器上，并制定监听事件为 接收事件
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);


        Set<SelectionKey> selectionKeys = selector.selectedKeys();
        Iterator<SelectionKey> iterator = selectionKeys.iterator();
        while (iterator.hasNext()) {
            SelectionKey selectionKey = iterator.next();
            if (selectionKey.isAcceptable()) {
                // A connection was accepted by a ServerSocketChannel.
            } else if (selectionKey.isReadable()) {
                // A channel is ready for reading.
            } else if (selectionKey.isWritable()) {
                // A channel is ready for writing.
            }
            iterator.remove();
        }
    }

}
