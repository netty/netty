package io.netty.nio.selector;

import org.junit.Test;

import javax.sound.midi.Track;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

        while (true) {
            int nReady = selector.select();
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey selectionKey = iterator.next();
                iterator.remove();

                if (selectionKey.isAcceptable()) {
                    // 创建新的连接，并且把连接注册到 selector 上，而且，声明这个 channel 只对读操作感兴趣
                    SocketChannel socketChannel = serverSocketChannel.accept();
                    socketChannel.configureBlocking(false);
                    socketChannel.register(selector, SelectionKey.OP_READ);
                } else if (selectionKey.isReadable()) {
                    SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                    readBuffer.clear();
                    socketChannel.read(readBuffer);

                    readBuffer.flip();
                    System.out.println("received : " + new String(readBuffer.array()));
                    selectionKey.interestOps(SelectionKey.OP_WRITE);
                } else if (selectionKey.isWritable()) {
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

        while (true) {
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

    @Test
    public void testSelector2() throws Exception {
        int[] ports = {8090, 8091, 8092, 8093, 8094, 8095};

        Selector selector = Selector.open();

//        System.out.println(SelectorProvider.provider().openSelector().getClass());

        for (int i = 0; i < ports.length; i++) {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            ServerSocket serverSocket = serverSocketChannel.socket();
            InetSocketAddress address = new InetSocketAddress(ports[i]);
            serverSocket.bind(address);

            // A selection key is created each time a channel is registered with a selector.
            SelectionKey selectionKey = serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Listening port： " + ports[i]);
        }

        while (true) {
            // 返回的 SelectionKey 数量
            int numbers = selector.select();

            System.out.println("numbers: " + numbers);

            // 获取所有连接键值 Set 集合
            Set<SelectionKey> selectionKeys = selector.selectedKeys();

            Iterator<SelectionKey> selectionKeyIterator = selectionKeys.iterator();
            while (selectionKeyIterator.hasNext()) {
                SelectionKey key = selectionKeyIterator.next();
                if (key.isAcceptable()) { // 处理客户端连接事件
                    ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
                    SocketChannel socketChannel = serverSocketChannel.accept();
                    socketChannel.configureBlocking(false);
                    socketChannel.register(selector, SelectionKey.OP_READ);
                    selectionKeyIterator.remove();
                    System.out.println("Get the client connection： " + socketChannel);
                } else if (key.isReadable()) { // 处理客户端 读事件
                    SocketChannel socketChannel = (SocketChannel) key.channel();
                    int byteRead = 0;

                    while (true) {
                        ByteBuffer buffer = ByteBuffer.allocate(512);
                        buffer.clear();
                        int read = socketChannel.read(buffer);
                        if (read <= 0) {
                            break;
                        }
                        buffer.flip();
                        socketChannel.write(buffer);
                        byteRead += read;
                    }
                    System.out.println("Read: " + byteRead + ", from: " + socketChannel);
                    selectionKeyIterator.remove();
                }
            }
        }
    }

    private Map<String, SocketChannel> clientMap = new HashMap<>();

    @Test
    public void testServer2() throws Exception {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);

        ServerSocket serverSocket = serverSocketChannel.socket();
        serverSocket.bind(new InetSocketAddress(8090));

        Selector selector = Selector.open();

        // 注册连接事件
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        while (true) {
            try {
                int numbers = selector.select();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                selectionKeys.forEach(key -> {
                    SocketChannel client = null;
                    try {
                        if (key.isAcceptable()) {
                            // 因为前面注册的是连接事件，所以这里强转为 ServerSocketChannel
                            ServerSocketChannel server = (ServerSocketChannel) key.channel();
                            client = server.accept();
                            client.configureBlocking(false);
                            // 注册 读事件
                            client.register(selector, SelectionKey.OP_READ);
                            String k = "[" + UUID.randomUUID() + "]";
                            clientMap.put(k, client);
                        } else if (key.isReadable()) {
                            client = (SocketChannel) key.channel();
                            ByteBuffer readBuffer = ByteBuffer.allocate(1024);
                            int read = client.read(readBuffer);
                            if (read > 0) {
                                readBuffer.flip();
                                Charset charset = Charset.forName("utf-8");
                                String receiveMsg = String.valueOf(charset.decode(readBuffer).array());
                                System.out.println(client + ": " + receiveMsg);

                                String sendKey = null;
                                for (Map.Entry<String, SocketChannel> entry : clientMap.entrySet()) {
                                    if (client == entry.getValue()) {
                                        sendKey = entry.getKey();
                                        break;
                                    }
                                }
                                for (Map.Entry<String, SocketChannel> entry : clientMap.entrySet()) {
                                    SocketChannel value = entry.getValue();
                                    ByteBuffer writeBuffer = ByteBuffer.allocate(1024);
                                    writeBuffer.put((sendKey + ": " + receiveMsg).getBytes(StandardCharsets.UTF_8));
                                    writeBuffer.flip();
                                    value.write(writeBuffer);
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                selectionKeys.clear();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testClient2() throws Exception {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        Selector selector = Selector.open();
        socketChannel.register(selector, SelectionKey.OP_CONNECT);
        socketChannel.connect(new InetSocketAddress("localhost", 8090));

        while (true) {
            selector.select();
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                if (key.isConnectable()) {
                    SocketChannel client = (SocketChannel) key.channel();
                    if (client.isConnectionPending()) {
                        client.finishConnect();
                        ByteBuffer writeBuffer = ByteBuffer.allocate(1024);
                        writeBuffer.put((LocalDateTime.now() + "connected successfully!").getBytes());
                        writeBuffer.flip();
                        client.write(writeBuffer);

                        ExecutorService executorService = Executors.newSingleThreadExecutor(Executors.defaultThreadFactory());
                        executorService.submit(() -> {
                            while (true) {
                                try {
                                    writeBuffer.clear();
                                    InputStreamReader isr = new InputStreamReader(System.in);
                                    BufferedReader br = new BufferedReader(isr);
                                    String sendMsg = br.readLine();
                                    writeBuffer.put(sendMsg.getBytes());
                                    writeBuffer.flip();
                                    client.write(writeBuffer);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }
                    client.register(selector, SelectionKey.OP_READ);
                } else if (key.isReadable()) {
                    SocketChannel client = (SocketChannel) key.channel();
                    ByteBuffer readBuffer = ByteBuffer.allocate(1024);
                    int read = client.read(readBuffer);
                    if (read > 0) {
                        String receivedMsg = new String(readBuffer.array(), 0, read);
                        System.out.println(receivedMsg);
                    }
                }
                iterator.remove();
            }
        }
    }
}
