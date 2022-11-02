package io.netty.nio.pipe;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.channels.Pipe;

/**
 * @author lxcecho 909231497@qq.com
 * @since 15.09.2021
 */
public class PipeTest {

    @Test
    public void test() throws Exception {
        // 获取管道
        Pipe pipe = Pipe.open();

        // 获取 Sink 通道
        Pipe.SinkChannel sinkChannel = pipe.sink();

        // 创建缓冲区
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.clear();

        // 写入数据
        buffer.put("lxcecho".getBytes());
        buffer.flip();

        while(buffer.hasRemaining()) {
            // Sink 发送数据
            sinkChannel.write(buffer);
        }

        // 获取 Source 通道
        Pipe.SourceChannel sourceChannel = pipe.source();

        // 读取数据
        ByteBuffer buffer2 = ByteBuffer.allocate(1024);

        int length = sourceChannel.read(buffer2);
        System.out.println(new String(buffer2.array(), 0, length));

        // 关闭通道
        sourceChannel.close();
        sinkChannel.close();

    }

}
