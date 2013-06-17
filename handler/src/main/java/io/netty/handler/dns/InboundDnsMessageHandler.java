package io.netty.handler.dns;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.MessageList;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.timeout.ReadTimeoutException;

public class InboundDnsMessageHandler extends ChannelInboundHandlerAdapter {

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		if (cause instanceof ReadTimeoutException) {
			DnsExchangeFactory.removeChannel(ctx.channel());
			return;
		}
		cause.printStackTrace();
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageList<Object> messages) {
		int size = messages.size();
		try {
			for (int i = 0; i < size; i++) {
				Object mesg = messages.get(i);
				if (mesg instanceof DnsResponse) {
					DnsCallback.finish((DnsResponse) mesg);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			messages.releaseAllAndRecycle();
		}
	}

}
