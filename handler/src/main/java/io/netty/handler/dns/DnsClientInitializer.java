package io.netty.handler.dns;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DnsQueryEncoder;
import io.netty.handler.codec.dns.DnsResponseDecoder;
import io.netty.handler.timeout.ReadTimeoutHandler;

public class DnsClientInitializer extends ChannelInitializer<NioDatagramChannel> {

	@Override
	protected void initChannel(NioDatagramChannel channel) throws Exception {
		channel.pipeline()
				.addLast("timeout", new ReadTimeoutHandler(30))
				.addLast("decoder", new DnsResponseDecoder())
				.addLast("encoder", new DnsQueryEncoder())
				.addLast("handler", new InboundDnsMessageHandler());
	}

}
