/**
 * Copyright (C), 2019-2019
 * FileName: AIOServerHandler
 * Author:   s·D·js
 * Date:     2019/4/22 22:16
 * Description: AIO处理器
 * Motto: 0.45%
 *
 * @create 2019/4/22
 * @since 1.0.0
 */


package com.lhh.aio;

import com.lhh.utils.CalculatorUtil;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

public class AIOServerHandler implements CompletionHandler<AsynchronousSocketChannel, AIOServer> {

    private final Integer BUFFER_SIZE = 1024;

    /**
     * 功能描述: <br>
     * 〈当操作完成时调用〉
     *
     * @param asynSocketChannel
     * @param attachment
     * @return:void
     * @since: 1.0.0
     * @Author:s·D·js
     * @Date: 2019/4/22 22:44
     */
    @Override
    public void completed(AsynchronousSocketChannel asynSocketChannel, AIOServer attachment) {
        //得到和调用方asynServerSocketChannel.accept(this, new AIOServerHandler());是相同的AIOServer对象
        System.out.println("attachment.hashCode() = " + attachment.hashCode());

        // 当有下一个客户端接入的时候 直接调用Server的accept方法，这样反复执行下去，保证多个客户端都可以阻塞
        attachment.asynServerSocketChannel.accept(attachment, this);
        read(asynSocketChannel);
    }

    private void read(final AsynchronousSocketChannel asynSocketChannel) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        asynSocketChannel.read(byteBuffer, byteBuffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer resultSize, ByteBuffer attachment) {
                //进行读取之后,重置标识位
                attachment.flip();
                //获取读取的数据
                String resultData = new String(attachment.array()).trim();
                System.out.println("Server -> " + "收到客户端的数据信息为:" + resultData);
                String response = resultData + " = " + CalculatorUtil.cal(resultData);
                write(asynSocketChannel, response);
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.out.println("呵呵，我read数据失败了！");
                exc.printStackTrace();
            }
        });
    }

    @Override
    public void failed(Throwable exc, AIOServer attachment) {
        System.out.println("呵呵，我AIOServerHandler失败了！");
        exc.printStackTrace();
    }

    // 写入数据
    public void write(final AsynchronousSocketChannel asynSocketChannel, String response) {
        try {
            //简单把数据写到缓冲区
            ByteBuffer buf = ByteBuffer.allocate(BUFFER_SIZE);
            buf.put(response.getBytes());
            buf.flip();
            // 在从缓冲区写入到通道中
            asynSocketChannel.write(buf).get();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
