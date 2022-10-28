package io.netty.nio.buffer;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * 验证 position、limit、capacity 指针变化
 *
 * private int position = 0; 指定下一个将要被写入或者读取的元素索引，它的值由 get/put 方法自动更新，在新创建一个 Buffer 对象时，position 被初始化为 0；
 * private int limit; 指定还有多少数据需要取出（在从缓冲区写入通道时），或者还有多少空间可以放入数据（在通道读入缓冲区时）；
 * private int capacity; 指定了可以存储在缓冲区中的最大数据容量，实际上，它指定了底层数组的大小，或者至少是制定了准许我们使用的底层数组的容量。
 * 关系：0<=position<=limit<=capacity
 *
 * @author lxcecho 909231497@qq.com
 * @since 23:51 27-10-2022
 */
public class BufferDemo {

    public static void main(String[] args) throws Exception {
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

    /**
     * 把这个缓冲区的实时状态打印出来
     *
     * @param step
     * @param buffer
     */
    private static void output(String step, ByteBuffer buffer) {
        System.out.println(step + ": ");
        // 容量，数组大小
        System.out.print("capacity: " + buffer.capacity() + ", ");
        // 当前操作数据所在的为止，也可以叫做游标
        System.out.print("position: " + buffer.position() + ", ");
        // 锁定值，flip，数据操作范围索引只能在 position-limit 之间
        System.out.print("limit: " + buffer.limit());
        System.out.println();
    }

}
