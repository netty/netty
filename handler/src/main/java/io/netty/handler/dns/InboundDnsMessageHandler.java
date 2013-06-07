package io.netty.handler.dns;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.timeout.ReadTimeoutException;

public class InboundDnsMessageHandler extends ChannelInboundMessageHandlerAdapter<DnsResponse> {

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		if (cause instanceof ReadTimeoutException) {
			DnsExchangeFactory.removeChannel(ctx.channel());
			return;
		}
		cause.printStackTrace();
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx,
			DnsResponse response) throws Exception {
		DnsCallback.finish(response);
	}

}
