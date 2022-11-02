package io.netty.nio.buffer;

import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

/**
 * @author lxcecho 909231497@qq.com
 * @since 10.09.2021
 */
public class BufferTest {

    @Test
    public void buffer11() throws Exception {
        FileInputStream fis = new FileInputStream("echo.txt");
        FileChannel channel = fis.getChannel();

        ByteBuffer buffer = ByteBuffer.allocate(10);
        output("initial", buffer);

        channel.read(buffer);
        output("read", buffer);

        /**
         * 此操作会会做两件事：
         * 1. 把 limit 设置为当前的 position
         * 2. 把 position 置为 0
         */
        buffer.flip();
        output("flip", buffer);

        while (buffer.remaining() > 0) {
            byte b = buffer.get();
        }
        output("get", buffer);

        buffer.clear();
        output("clear", buffer);

        fis.close();

    }

    private void output(String step, Buffer buffer) {
        System.out.println("step: " + step);
        System.out.println("capacity: " + buffer.capacity());
        System.out.println("position: " + buffer.position());
        System.out.println("limit: " + buffer.limit());
        System.out.println("-----------------------");
    }

    @Test
    public void buffer10() throws Exception {
        // 1 获取 Selector 选择器
        Selector selector = Selector.open();

        // 2 获取通道
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();

        // 3 设置为非阻塞
        serverSocketChannel.configureBlocking(false);

        // 4 绑定连接
        serverSocketChannel.bind(new InetSocketAddress(9999));

        // 5 将通道注册到选择器上，并指定监听事件为：“接收”事件
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    private static final int start = 0;
    private static final int size = 1024;

    /**
     * 内存映射文件 IO
     *
     * @throws Exception
     */
    @Test
    public void buffer06() throws Exception {
        RandomAccessFile file = new RandomAccessFile("D:\\file01.txt", "rw");
        FileChannel channel = file.getChannel();
        MappedByteBuffer map = channel.map(FileChannel.MapMode.READ_WRITE, start, size);

        map.put(0, (byte) 97);
        map.put(1023, (byte) 122);
        file.close();
    }

    /**
     * 直接缓冲区
     *
     * @throws Exception
     */
    @Test
    public void buffer05() throws Exception {
        String inFile = "D:\\file01.txt";
        FileInputStream fis = new FileInputStream(inFile);
        FileChannel fisChannel = fis.getChannel();

        String outFile = "D:\\file02.txt";
        FileOutputStream fos = new FileOutputStream(outFile);
        FileChannel fosChannel = fos.getChannel();

        // 创建直接缓冲区
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024);

        while (true) {
            buffer.clear();
            int read = fisChannel.read(buffer);
            if (read == -1) {
                break;
            }
            buffer.flip();
            fosChannel.write(buffer);
        }
    }

    /**
     * 只读缓冲区
     *
     * @throws Exception
     */
    @Test
    public void buffer04() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        for (int i = 0; i < buffer.capacity(); i++) {
            buffer.put((byte) i);
        }
        // 创建只读缓冲区
        ByteBuffer readOnlyBuffer = buffer.asReadOnlyBuffer();
        // 改变原缓冲区的内容
        for (int i = 0; i < buffer.capacity(); i++) {
            byte b = buffer.get(i);
            b *= 10;
            buffer.put(i, b);
        }

        readOnlyBuffer.position(0);
        readOnlyBuffer.limit(buffer.capacity());

        while (readOnlyBuffer.remaining() > 0) {
            System.out.print(readOnlyBuffer.get() + " ");
        }
    }

    /**
     * 缓冲区分片
     *
     * @throws Exception
     */
    @Test
    public void buffer03() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        for (int i = 0; i < buffer.capacity(); i++) {
            buffer.put((byte) i);
        }

        // 创建子缓冲区
        buffer.position(3);
        buffer.limit(7);
        ByteBuffer slice = buffer.slice();

        // 改变子缓冲区内容
        for (int i = 0; i < slice.capacity(); i++) {
            byte b = slice.get(i);
            b *= 10;
            slice.put(i, b);
        }

        buffer.position(0);
        buffer.limit(buffer.capacity());

        while (buffer.remaining() > 0) {
            System.out.print(buffer.get() + " ");
        }
    }

    @Test
    public void buffer02() throws Exception {
        /**
         * 分配新的 int 缓冲区，参数为缓冲区容量
         * 新缓冲区的当前位置将为 0，其界限（限制位置）为将为其容量
         * 他将具有一个底层实现数组，其数组偏移量将为 0
         */
        IntBuffer intBuffer = IntBuffer.allocate(8);
        for (int i = 0; i < intBuffer.capacity(); i++) {
            int j = 2 * (i + 1);
            // 将给定整数写入此缓冲区的当前位置，当前位置递增
            intBuffer.put(j);
        }

        // 重置缓冲区
        intBuffer.flip();

        while (intBuffer.hasRemaining()) {
            int value = intBuffer.get();
            System.out.println(String.valueOf(value));
        }
    }

    @Test
    public void buffer01() throws Exception {
        // fileChannel
        RandomAccessFile file = new RandomAccessFile("D:\\file01.txt", "rw");
        FileChannel channel = file.getChannel();

        // 创建 buffer 大小
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);

        // read
        int byteRead = channel.read(byteBuffer);

        while (byteRead != -1) {
            // read 模式
            byteBuffer.flip();
            while (byteBuffer.hasRemaining()) {
                System.out.println((char) byteBuffer.get());
            }
            byteBuffer.clear();
            byteRead = channel.read(byteBuffer);
        }
        file.close();
    }

}
