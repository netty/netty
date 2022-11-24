package io.netty.netty.groupchat.case2;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author lxcecho 909231497@qq.com
 * @since 11.12.2021
 */
public class GroupChatServerHandler extends SimpleChannelInboundHandler<String> {

    // public static List<Channel> channels = new ArrayList<>();

    // 使用一个 HashMap 管理
    // public static Map<String,Channel> channels = new HashMap<>();

    // 定义一个 channel 组，管理所有的 channel
    // GlobalEventExecutor.INSTANCE 是全局的事件执行器，是一个单例
    private static ChannelGroup channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * handlerAdded 表示连接建立，一旦连接，第一个被执行
     * 将当前 channel 加入到 channelGroup
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        // 将该客户加入聊天的信息推送给其他在线的客户端
        // 该方法会将 channelGroup 中所有的 channel 遍历，并发送消息，我们不需要自己遍历
        channelGroup.writeAndFlush("[客户端]" + channel.remoteAddress() + " 加入聊天 " + sdf.format(new Date()) + "\n");
        channelGroup.add(channel);
    }

    /**
     * 断开连接，将 XX客户端 离开消息推送给当前在线的客户
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        channelGroup.writeAndFlush("[客户端]" + channel.remoteAddress() + " 离开了\n");
        System.out.println("channelGroupSize: " + channelGroup.size());
    }

    /**
     * 表示 channel 处于活动状态，提示 XX上线
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println(ctx.channel().remoteAddress() + " 上线了~");
    }

    /**
     * 表示 channel 处于不活动状态，提示 XX离线
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println(ctx.channel().remoteAddress() + " 离线了~");
    }

    /**
     * 读取数据
     *
     * @param channelHandlerContext
     * @param s
     * @throws Exception
     */
    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, String s) throws Exception {
        // 获取到当前 channel
        Channel channel = channelHandlerContext.channel();
        // 这时我们遍历 channelGroup，根据不同的情况，回送不同的消息
        channelGroup.forEach(ch -> {
            if(channel != ch) { // 不是当前的 channel，转发消息
                ch.writeAndFlush("[客户]" + channel.remoteAddress() + " 发送了消息" + s + "\n");
            } else { // 回显自己发送的消息给自己
                ch.writeAndFlush("[自己]发送了消息" + s + "\n");
            }
        });
    }

    /**
     * 处理异常
     *
     * @param ctx
     * @param cause
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // 关闭通道
        ctx.close();
    }
}
