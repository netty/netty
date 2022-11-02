package io.netty.nio;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * @author lxcecho 909231497@qq.com
 * @since 03.04.2022
 */
@Slf4j
public class NioTest {

    @Test
    public void test12() throws Exception {

    }

    /**
     * Buffer 的 Scattering 和 Gathering
     *
     * 使用 telnet/nc 作为客户端测试
     *
     * @throws Exception
     */
    @Test
    public void test11() throws Exception {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        InetSocketAddress inetSocketAddress = new InetSocketAddress(8899);
        serverSocketChannel.socket().bind(inetSocketAddress);

        int messageLength = 2 + 3 + 4;
        ByteBuffer[] buffers = new ByteBuffer[3];

        buffers[0] = ByteBuffer.allocate(2);
        buffers[1] = ByteBuffer.allocate(3);
        buffers[2] = ByteBuffer.allocate(4);

        SocketChannel socketChannel = serverSocketChannel.accept();
        while (true) {
            int read = 0;
            while (read < messageLength) {
                long r = socketChannel.read(buffers);
                read += r;
                log.info("read: {}", read);

                Arrays.asList(buffers).stream().map(buffer -> "position: " + buffer.position() + ", limit: " + buffer.limit())
                        .forEach(log::info);
            }

            Arrays.asList(buffers).forEach(buffer->{
                buffer.flip();
            });

            long write = 0;
            while (write < messageLength) {
                long w = socketChannel.write(buffers);
                write+=w;
                log.info("write: {}, read: {}, messageLength: {}", write, read, messageLength);
            }
        }
    }

    @Test
    public void test10() throws Exception {
        RandomAccessFile randomAccessFile = new RandomAccessFile("text10.txt", "rw");
        FileChannel channel = randomAccessFile.getChannel();
        FileLock lock = channel.lock(3, 6, true);
        log.info("lock isValid?: {}", lock.isValid());
        log.info("lock type: {}", lock.isShared());
        lock.release();
        randomAccessFile.close();
    }

    @Test
    public void test09() throws Exception {
        RandomAccessFile randomAccessFile = new RandomAccessFile("text9.txt", "rw");
        FileChannel channel = randomAccessFile.getChannel();
        MappedByteBuffer mappedByteBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, 5);
        // 直接从内存中修改
        mappedByteBuffer.put(0, (byte) 'a');
        mappedByteBuffer.put(3, (byte) 'b');
        randomAccessFile.close();
    }

    @Test
    public void test08() throws Exception {
        FileInputStream fis = new FileInputStream("input.txt");
        FileOutputStream fos = new FileOutputStream("output.txt");
        FileChannel inputChannel = fis.getChannel();
        FileChannel outputChannel = fos.getChannel();

        ByteBuffer buffer = ByteBuffer.allocate(1024);

        while (true) {
            // 如果注释掉，会发生什么情况？？？  不断写入文件
            buffer.clear(); // 作用是将 buffer 的三个核心属性归位

            // 将 输入文件通道 内容写入 buffer
            int read = inputChannel.read(buffer);
            log.info("read: {}", read);
            if (-1 == read) {
                break;
            }

            buffer.flip();
            // 将 buffer 内容写入 输出文件通道
            outputChannel.write(buffer);
        }
        inputChannel.close();
        outputChannel.close();
    }

    /**
     * 只读 buffer 我们可以随时将一个 普通buffer 调用 asReadOnlyBuffer() 返回一个只读 buffer；
     * 但不能将一个 只读 buffer 转换为 读写 buffer。
     *
     * @throws Exception
     */
    @Test
    public void test07() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(10);

        log.info("buffer: {}", buffer.getClass()); // class java.nio.HeapByteBuffer

        for (int i = 0; i < buffer.capacity(); i++) {
            buffer.put((byte) i);
        }

        ByteBuffer readOnlyBuffer = buffer.asReadOnlyBuffer();

        log.info("readOnlyBuffer: {}", readOnlyBuffer.getClass()); // class java.nio.HeapByteBufferR

        readOnlyBuffer.position(0);
    }

    /**
     * SliceBuffer 与 原有的 Buffer 共享相同的底层数组
     *
     * @throws Exception
     */
    @Test
    public void test06() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        for (int i = 0; i < buffer.capacity(); i++) {
            buffer.put((byte) i);
        }

        buffer.position(2); // 2
        buffer.limit(6); //6

        log.info("------------------------------{}", buffer.get());

        ByteBuffer slice = buffer.slice(); //5

        for (int i = 0; i < slice.capacity(); i++) {
            byte b = slice.get(i);
            b *= 2;
            slice.put(i, b);
        }

        buffer.position(0);
        buffer.limit(buffer.capacity());

        while (buffer.hasRemaining()) {
            log.info("e: {}", buffer.get());
        }
    }

    /**
     * 类型化的 put() 与 get(0 方法
     *
     * @throws Exception
     */
    @Test
    public void test05() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(64);

        buffer.putInt(15);
        buffer.putLong(4444444444L);
        buffer.putDouble(6.53234243);
        buffer.putChar('昌');
        buffer.putShort((short) 2);
        buffer.putChar('我');

        buffer.flip();

        log.info("{}", buffer.getInt());
        log.info("{}", buffer.getLong());
        log.info("{}", buffer.getDouble());
        log.info("{}", buffer.getChar());
        log.info("{}", buffer.getShort());
        log.info("{}", buffer.getChar());
    }

    @Test
    public void test04() throws Exception {
        FileInputStream fis = new FileInputStream("input.txt");
        FileOutputStream fos = new FileOutputStream("output.txt");
        FileChannel inputChannel = fis.getChannel();
        FileChannel outputChannel = fos.getChannel();

        ByteBuffer buffer = ByteBuffer.allocate(1024);

        while (true) {
            // 如果注释掉，会发生什么情况？？？  不断写入文件
            buffer.clear(); // 作用是将 buffer 的三个核心属性归位

            // 将 输入文件通道 内容写入 buffer
            int read = inputChannel.read(buffer);
            log.info("read: {}", read);
            if (-1 == read) {
                break;
            }

            buffer.flip();
            // 将 buffer 内容写入 输出文件通道
            outputChannel.write(buffer);
        }
        inputChannel.close();
        outputChannel.close();
    }

    @Test
    public void test03() throws Exception {
        FileOutputStream fos = new FileOutputStream("echo.txt");
        FileChannel channel = fos.getChannel();

        ByteBuffer buffer = ByteBuffer.allocate(512);
        byte[] message = "Hello, I'am netty.".getBytes(StandardCharsets.UTF_8);

        for (int i = 0; i < message.length; i++) {
            buffer.put(message[i]);
        }

        buffer.flip();
        // 将 buffer 数据写入 channel
        channel.write(buffer);

        fos.close();
        channel.close();

    }

    @Test
    public void test02() throws Exception {
        FileInputStream fis = new FileInputStream("echo.txt");
        FileChannel channel = fis.getChannel();

        ByteBuffer buffer = ByteBuffer.allocate(512);
        // 将 channel 中的数据读到 buffer
        channel.read(buffer);

        // 读写转换
        buffer.flip();

        while (buffer.remaining() > 0) {
            byte b = buffer.get();
            log.info("b: {}", (char) b);
        }
        fis.close();
        channel.close();
    }

    @Test
    public void test01() {
        IntBuffer buffer = IntBuffer.allocate(10);
        for (int i = 0; i < buffer.capacity(); i++) {
            int randomNumber = new SecureRandom().nextInt(20);
            buffer.put(randomNumber);
        }

        buffer.flip();

        while (buffer.hasRemaining()) {
            log.info("buffer: {}", buffer.get());
        }
    }
}
