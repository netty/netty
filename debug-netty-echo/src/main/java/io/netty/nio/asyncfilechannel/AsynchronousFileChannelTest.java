package io.netty.nio.asyncfilechannel;

import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Future;

/**
 * @author lxcecho 909231497@qq.com
 * @since 15.09.2021
 */
public class AsynchronousFileChannelTest {

    @Test
    public void testCompletionHandlerWrite() throws IOException {
        Path path = Paths.get("D:\\test\\file01.txt");
        AsynchronousFileChannel asynchronousFileChannel =
                AsynchronousFileChannel.open(path, StandardOpenOption.WRITE);

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        long position = 0;

        buffer.put("testCompletionHandlerWrite".getBytes());
        buffer.flip();

        asynchronousFileChannel.write(buffer, position, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer result, ByteBuffer attachment) {
                System.out.println("bytes written : " + result);
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {

            }
        });

        System.out.println("Write over!");

    }

    @Test
    public void testFutureWrite() throws IOException {
        Path path = Paths.get("D:\\test\\file01.txt");
        // StandardOpenOption.WRITE 以写模式打开
        AsynchronousFileChannel asynchronousFileChannel =
                AsynchronousFileChannel.open(path, StandardOpenOption.WRITE);

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        long position = 0;

        buffer.put("testFutureWrite".getBytes());
        buffer.flip();

        // 文件必须存在，否则 write 抛出一个 java.nio.file.NoSuchFileException
        Future<Integer> future = asynchronousFileChannel.write(buffer, position);
        buffer.clear();

        while (!future.isDone());

        System.out.println("Write over!");
    }

    @Test
    public void testCompletionHandlerRead() throws IOException {
        Path path = Paths.get("D:\\test\\file01.txt");
        AsynchronousFileChannel asynchronousFileChannel =
                AsynchronousFileChannel.open(path, StandardOpenOption.READ);

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        long position = 0;

        asynchronousFileChannel.read(buffer, position, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            /**
             * result：读取的字节数
             *
             * @param result
             * @param attachment
             */
            @Override
            public void completed(Integer result, ByteBuffer attachment) {
                System.out.println("result: " + result);

                attachment.flip();
                byte[] data = new byte[attachment.limit()];
                attachment.get(data);
                System.out.println(new String(data));
                attachment.clear();
            }

            /**
             * 读取失败的话 执行 failed 方法
             *
             * @param exc
             * @param attachment
             */
            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {

            }
        });
    }

    @Test
    public void testFutureRead() throws IOException {

        // 1 创建 AsynchronousFileChannel
        Path path = Paths.get("D:\\test\\file01.txt");
        // StandardOpenOption.READ 表示该文件将被打开阅读
        AsynchronousFileChannel asynchronousFileChannel =
                AsynchronousFileChannel.open(path, StandardOpenOption.READ);

        // 2 创建 Buffer
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        // 3 调用 channel 的 read 方法得到 Future
        Future<Integer> future = asynchronousFileChannel.read(buffer, 0);

        // 4 判断是否完成 isDone，直到返回 true
        while (!future.isDone()) ;

        // 5 读取数据到 Buffer 里面
        buffer.flip();
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);
        System.out.println(new String(bytes));
        buffer.clear();

    }

}
