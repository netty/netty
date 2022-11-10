package io.netty.netty.heartbeat;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * @author lxcecho 909231497@qq.com
 * @since 12.12.2021
 */
public class MyServerHandler extends ChannelInboundHandlerAdapter {
    /**
     * 用户事件触发
     *
     * @param ctx 上下文
     * @param evt 事件
     * @throws Exception
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if(evt instanceof IdleStateEvent) {
            // 将 evt 向下转型 IdleStateEvent
            IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
            String eventType = null;
            switch(idleStateEvent.state()) {
                case READER_IDLE:
                    eventType = "读空闲";
                    break;
                case WRITER_IDLE:
                    eventType = "写空闲";
                    break;
                case ALL_IDLE:
                    eventType = "读写空闲";
                    break;
            }

            System.out.println(ctx.channel().remoteAddress() + " 超时时间：" + eventType);
            System.out.println("服务器做响应处理");

            // 如果发生空闲，我们关闭通道
//            ctx.channel().close();
        }
    }
}
