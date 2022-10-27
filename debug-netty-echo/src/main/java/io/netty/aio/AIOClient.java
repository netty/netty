package io.netty.aio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author lxcecho 909231497@qq.com
 * @since 23:28 27-10-2022
 */
public class AIOClient {

    private final AsynchronousSocketChannel socketChannel;

    public AIOClient() throws IOException {
        this.socketChannel = AsynchronousSocketChannel.open();
    }

    public void connect(String host, int port) {
        socketChannel.connect(new InetSocketAddress(host, port), null, new CompletionHandler<Void, Void>() {
            /**
             * 发送一串字符到服务端
             *
             * @param result
             *          The result of the I/O operation.
             * @param attachment
             *          The object attached to the I/O operation when it was initiated.
             */
            @Override
            public void completed(Void result, Void attachment) {
                try {
                    socketChannel.write(ByteBuffer.wrap("这是一条测试数据".getBytes(StandardCharsets.UTF_8))).get();
                    System.out.println("已发送至服务器");
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                exc.printStackTrace();
            }
        });

        // 处理服务端发送过来的结果
        final ByteBuffer bb = ByteBuffer.allocate(1024);
        socketChannel.read(bb, null, new CompletionHandler<Integer, Object>() {
            @Override
            public void completed(Integer result, Object attachment) {
                System.out.println("IO 操作完成：" + result);
                System.out.println("获取反馈结果：" + new String(bb.array()));
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                exc.printStackTrace();
            }
        });

        try {
            TimeUnit.MICROSECONDS.sleep(Integer.MAX_VALUE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        new AIOClient().connect("localhost", 8090);
    }

}
