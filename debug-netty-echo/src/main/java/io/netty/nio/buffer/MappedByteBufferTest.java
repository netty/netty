package io.netty.nio.buffer;

import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * IO 映射缓冲区：MappedByteBuffer
 * MappedByteBuffer 可让文件直接在内存(堆外内存)修改, 操作系统不需要拷贝一次。
 * NIO 还提供了 MappedByteBuffer，可以让文件直接在内存（堆外的内存）中进行修改，而如何同步到文件由 NIO 来完成。
 *
 * @author lxcecho 909231497@qq.com
 * @since 23:10 28-10-2022
 */
public class MappedByteBufferTest {

    public static void main(String[] args) throws Exception {
        RandomAccessFile randomAccessFile = new RandomAccessFile("2.txt", "rw");
        // 获取对应通道
        FileChannel fileChannel = randomAccessFile.getChannel();

        /**
         * 把缓冲区跟文件系统进行一个映射关联，只要操作缓冲区里面的内容，文件内容也会跟着改变
         *
         * public abstract MappedByteBuffer map(MapMode mode,
         *                                          long position, long size)
         *
         * mode：FileChannel.MapMode.READ_WRITE 使用的读写模式；
         * position：可以直接修改的起始位置；
         * size：具体映射到内存的大小（不是索引位置），即将 2.txt 的多少个字节映射到内存
         *
         * 可以直接修改的范围就是 0-5
         * 实际类型 DirectByteBuffer
         */
        MappedByteBuffer mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, 5);
        mappedByteBuffer.put(0,(byte) 'H');
        mappedByteBuffer.put(3,(byte) '9');
//        mappedByteBuffer.put(5,(byte) 'Y');// IndexOutOfBoundsException

        randomAccessFile.close();
        System.out.println("修改成功...");// 修改成功之后去到磁盘打开文件，发现修改成功！
    }

}
