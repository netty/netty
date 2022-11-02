package io.netty.nio.channel;

import org.junit.Test;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author lxcecho 909231497@qq.com
 * @since 11.09.2021
 */
public class FileChannelTest {

    /**
     * 使用 FileChannel.transferTo() 完成文件之间的复制
     *
     * @throws Exception
     */
    @Test
    public void channel04() throws Exception {
        RandomAccessFile fromFile = new RandomAccessFile("D:\\file01.txt", "rw");
        FileChannel fromChannel = fromFile.getChannel();

        RandomAccessFile toFile = new RandomAccessFile("D:\\file02.txt", "rw");
        FileChannel toChannel = toFile.getChannel();

        long position = 0;
        long count = fromChannel.size();

        fromChannel.transferTo(position, count, toChannel);

        fromFile.close();
        toFile.close();
        System.out.println("over ...");

    }

    /**
     * 使用 FileChannel.transferFrom() 完成文件之间的复制
     *
     * @throws Exception
     */
    @Test
    public void channel03() throws Exception {
        RandomAccessFile formFile = new RandomAccessFile("D:\\file01.txt", "rw");
        FileChannel fromChannel = formFile.getChannel();

        RandomAccessFile toFile = new RandomAccessFile("D:\\file02.txt", "rw");
        FileChannel toChannel = toFile.getChannel();

        long position = 0;
        long count = fromChannel.size();

        /**
         * position: 从 position 处开始向目标文件中写入数据
         * count: 表示最多传输的字节数
         * 如果源通道的剩余空间小于 count 个字节，则所传输的字节数要小于请求的字节数。
         */
        toChannel.transferFrom(fromChannel, position, count);

        formFile.close();
        toFile.close();
        System.out.println("file copy over...");

    }

    /**
     * 向 FileChannel 中写入数据
     *
     * @throws Exception
     */
    @Test
    public void channel02() throws Exception {
        RandomAccessFile file = new RandomAccessFile("D:\\file02.txt", "rw");
        FileChannel channel = file.getChannel();

        String data = "New data to write to file ... " + System.currentTimeMillis();

        ByteBuffer buffer = ByteBuffer.allocate(48);
        buffer.clear();
        buffer.put(data.getBytes());

        buffer.flip();

        /**
         * FileChannel.write() 是在 while 中循环调用的，因为无法保证 write 方法一次向 FileChannel 写入多少字节，
         * 因此需要重复调用 wirte 方法，直到 Buffer 中已经没有尚未写入通道的字节。
         */
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
        // 用完 Channel 需将其关闭
        channel.close();
    }

    /**
     * 从 FileChannel 中读取数据
     *
     * @throws Exception
     */
    @Test
    public void channel() throws Exception {
        // 打开 FileChannel，通过 InputStream/OutputStream/RandomAccessFile 获取一个 FileChannel 实例
        RandomAccessFile file = new RandomAccessFile("D:\\file01.txt", "rw");
        FileChannel channel = file.getChannel();

        // 将数据写入 缓冲区
        ByteBuffer buffer = ByteBuffer.allocate(48);
        // read 表示有多少字节被读到了 buffer中，如果返回 -1，表示文件末尾。
        int read = channel.read(buffer);

        while (read != -1) {
            System.out.println("read : " + read);
            // 反转读写模式
            buffer.flip();
            while (buffer.hasRemaining()) {
                // 从缓冲区读取数据
                System.out.println((char) buffer.get());
            }
            // 清除缓冲区内容
//            buffer.compact();
            buffer.clear();
            read = channel.read(buffer);
        }
        file.close();
        System.out.println("handle over...");
    }

}
