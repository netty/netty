package io.netty.example.localecho;

import io.netty.channel.ChannelInboundHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;

public class LocalEchoClientHandler extends ChannelInboundMessageHandlerAdapter<String> {

    @Override
    public void messageReceived(ChannelInboundHandlerContext<String> ctx, String msg) {
        // Print as received
        System.out.println(msg);
    }

    @Override
    public void exceptionCaught(ChannelInboundHandlerContext<String> ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
