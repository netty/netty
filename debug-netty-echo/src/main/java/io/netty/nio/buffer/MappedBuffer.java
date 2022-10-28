package io.netty.nio.buffer;

import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * IO 映射缓冲区
 *
 * @author lxcecho 909231497@qq.com
 * @since 23:10 28-10-2022
 */
public class MappedBuffer {

    private static final int start = 0;

    private static final int size = 1024;

    public static void main(String[] args) throws Exception {
        RandomAccessFile randomAccessFile = new RandomAccessFile("E:\\lxcecho.txt", "rw");
        FileChannel fc = randomAccessFile.getChannel();

        // 把缓冲区跟文件系统进行一个映射关联，只要操作缓冲区里面的内容，文件内容也会跟着改变
        MappedByteBuffer mappedByteBuffer = fc.map(FileChannel.MapMode.READ_WRITE, start, size);

        mappedByteBuffer.put(0, (byte) 97);
        mappedByteBuffer.put(1023, (byte) 122);

        randomAccessFile.close();
    }

}
