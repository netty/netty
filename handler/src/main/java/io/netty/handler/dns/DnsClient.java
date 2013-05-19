package io.netty.handler.dns;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import bakkar.mohamed.dnscodec.DnsQuery;

public class DnsClient {

	private static final Map<byte[], DnsServerInfo> dnsServers = new HashMap<byte[], DnsServerInfo>();

	public static void resolveQuery(DnsTransmission resolver) throws InterruptedException, UnknownHostException {
		DnsServerInfo info = null;
		synchronized (DnsClient.class) {
			info = dnsServers.get(resolver.dnsAddress());
			if (info == null) {
				EventLoopGroup group = new NioEventLoopGroup();
				InetSocketAddress address = new InetSocketAddress(InetAddress.getByAddress(resolver.dnsAddress()), 53);
				Bootstrap b = new Bootstrap();
				b.group(group)
				.channel(NioDatagramChannel.class)
				.option(ChannelOption.SO_BROADCAST, true)
				.handler(new TransmissionInitializer(resolver));
				dnsServers.put(resolver.dnsAddress(), info = new DnsServerInfo(resolver.dnsAddress(), b.connect(address).sync().channel()));
			} else {
				info.setActiveCount(info.getActiveCount() + 1);
			}
		}
		Channel channel = info.channel();
		DnsQuery query = new DnsQuery(resolver.id());
		query.addQuestion(resolver.question());
		channel.write(query).sync();
		synchronized (resolver) {
			resolver.wait(5000l);
		}
		completeRequest(channel.pipeline().context("handler"), info);
	}

	private static synchronized void completeRequest(ChannelHandlerContext ctx, DnsServerInfo info) throws InterruptedException {
		if (info.getActiveCount() == 1) {
			Channel channel = info.channel();
			try {
				dnsServers.remove(info.address());
				ctx.close().sync();
				channel.close().sync();
			} finally {
				channel.eventLoop().shutdownGracefully();
			}
		} else {
			info.setActiveCount(info.getActiveCount() - 1);
		}
	}

}
