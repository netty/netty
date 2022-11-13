package io.netty.nio.buffer;

import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
import java.nio.charset.StandardCharsets;

/**
 * Java NIO 中拥有三个概念：Selector、Buffer、Channel。即 NIO 是面向快（block）或是缓冲区（buffer） 编程的。Buffer 本身就是一块内存，
 * 底层实现上，它实际上就是个数组。数据的读、写都是通过 Buffer 来实现的。
 * 除了数组之外，Buffer 还提供了对于数据的结构化访问方式，并且可以追踪到系统的读写逻辑。
 * Java 中的 8 中原生数据类型都有各自对应的 Buffer 类型，如 CharBuffer、IntBuffer等。
 * Channel 指的是可以向其写入数据或是从中读取数据的对象，他类似于 java.io 中的 Stream。
 * 所有数据的读写都是通过 Buffer 来进行的，永远不会出现直接向 Channel 写入数据的情况，或是直接从 Channel 读取数据的情况。
 * 与 Stream 不同的是，Channel 是双向的，一个流只可能是 InputStream 或是 OutputStream，Channel 打开后则可以进行读取、写入或是读写。
 * 由于 Channel 是双向的，因此它能更好的反映出底层操作系统的真实情况：在 Linux 系统中，底层操作系统的通道就是双向的。
 *
 * @author lxcecho 909231497@qq.com
 * @since 23:39 27-10-2022
 */
public class BufferTest {

    @Test
    public void buffer05() throws Exception {
        /**
         * 分配新的 int 缓冲区，参数为缓冲区容量，新缓冲区的当前位置为 0，其界限（限制位置）为其容量，
         * 它具有一个底层实现数组，其数组偏移量为 0；
         */
        IntBuffer buffer = IntBuffer.allocate(8);
        for (int i = 0; i < buffer.capacity(); i++) {
            int j = 2 * (i + 1);
            // 将给定证书写入此缓冲区的当前位置，当前位置递增
            buffer.put(j);
        }
        // 重设此缓冲区，将限制位置设置为当前位置，然后将当前位置设置为 0
        buffer.flip();
        // 查看在当前位置和限制位置之间是否有元素
        while (buffer.hasRemaining()) {
            // 读取此缓冲区当前位置的整数，然后当前位置递增
            int j = buffer.get();
            System.out.print(j + " ");
        }
    }

    /**
     * 验证 position、limit、capacity 指针变化
     * private int position = 0; 指定下一个将要被写入或者读取的元素索引，它的值由 get/put 方法自动更新，在新创建一个 Buffer 对象时，position 被初始化为 0；
     * private int limit; 指定还有多少数据需要取出（在从缓冲区写入通道时），或者还有多少空间可以放入数据（在通道读入缓冲区时）；
     * private int capacity; 指定了可以存储在缓冲区中的最大数据容量，实际上，它指定了底层数组的大小，或者至少是制定了准许我们使用的底层数组的容量。
     * 关系：0<=position<=limit<=capacity
     *
     * @throws Exception
     */
    @Test
    public void buffer04() throws Exception {

//        m1();

        m2();
    }

    private void m2() throws IOException {
        // 文件 IO 处理，内容：lxcecho
        FileInputStream fis = new FileInputStream("E:\\lxcecho.txt");
        // 创建文件的操作管道
        FileChannel fc = fis.getChannel();

        // 分配一个 10 个大小的缓冲区，其实就是分配一个 10 个大小的 Byte 数组
        ByteBuffer buffer = ByteBuffer.allocate(10);
        output("Initial", buffer);

        // 先读一下
        fc.read(buffer);
        output("read", buffer);

        // 准备操作之前，先锁定操作范围
        // 作用：一是把 limit 设置为当前的 position 值，二是把 position 设置为 0
        buffer.flip();
        output("flip", buffer);

        // 判断有没有可读数据
        System.out.print("content: ");
        while (buffer.remaining() > 0) {
            byte b = buffer.get();
            System.out.print((char) b);
        }
        System.out.println();

        // 调用 get
        output("get", buffer);

        // 可以理解为解锁
        buffer.clear();
        output("clear", buffer);

        // 最后关闭通道
        fc.close();
    }

    private void m1() throws IOException {
        FileInputStream fis = new FileInputStream("E:\\lxcecho.txt");
        FileChannel channel = fis.getChannel();

        ByteBuffer buffer = ByteBuffer.allocate(512);
        output("initial", buffer);

        // 从此通道读取字节序列到给定缓冲区
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

    /**
     * 把这个缓冲区的实时状态打印出来
     *
     * @param step
     * @param buffer
     */
    private void output(String step, Buffer buffer) {
        System.out.println("step: " + step);
        // 容量，数组大小
        System.out.print("capacity: " + buffer.capacity() + ", ");
        // 当前操作数据所在的为止，也可以叫做游标
        System.out.print("position: " + buffer.position() + ", ");
        // 锁定值，flip，数据操作范围索引只能在 position-limit 之间
        System.out.print("limit: " + buffer.limit());
        System.out.println();
    }

    @Test
    public void buffer03() throws Exception {
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
            System.out.println(value);
        }
    }

    /**
     * Buffer 的简单使用
     */
    @Test
    public void testBuffer() {
        // 创建一个 Buffer，大小为5，既可以存放5个 int
        IntBuffer intBuffer = IntBuffer.allocate(5);

        // 向 buffer 存放数据
        for (int i = 0; i < intBuffer.capacity(); i++) {
            intBuffer.put(i*2);
        }

        // 从 buffer 读取数据
        // 将 buffer 转换，读写切换
        intBuffer.flip();

        while (intBuffer.hasRemaining()){
            System.out.println(intBuffer.get());
        }
    }

    @Test
    public void testChannelReadOrWrite() throws Exception {
//        fileChannelRead();
//        fileChannelWrite();
        fileReadOrWrite();
    }

    public static void fileReadOrWrite() throws Exception {
        FileInputStream fis = new FileInputStream("E:\\input.txt");
        FileOutputStream fos = new FileOutputStream("E:\\output.txt");

        FileChannel fisChannel = fis.getChannel();
        FileChannel fosChannel = fos.getChannel();

        ByteBuffer buffer = ByteBuffer.allocate(512);

        while (true) {
            // 如果注释掉该行代码，会发生什么？
            buffer.clear();
            int read = fisChannel.read(buffer);
            System.out.println("read: " + read);
            if (-1 == read) {
                break;
            }
            buffer.flip();
            fosChannel.write(buffer);
        }
    }

    private static void fileChannelWrite() throws IOException {
        FileOutputStream fos = new FileOutputStream("E:\\lxcecho.txt");
        FileChannel channel = fos.getChannel();

        ByteBuffer buffer = ByteBuffer.allocate(512);
        byte[] msg = "Hello lxcecho!".getBytes(StandardCharsets.UTF_8);

        for (int i = 0; i < msg.length; i++) {
            buffer.put(msg[i]);
        }

        buffer.flip();

        // 将一个字节序列从给定的缓冲区写入此通道
        channel.write(buffer);

        fos.close();
    }

    private static void fileChannelRead() throws IOException {
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
