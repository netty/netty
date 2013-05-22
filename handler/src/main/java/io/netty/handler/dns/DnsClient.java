package bakkar.mohamed.dnsresolver;

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

	public static synchronized DnsServerInfo serverInfo(byte[] address) throws UnknownHostException, InterruptedException {
		DnsServerInfo info = dnsServers.get(address);
		if (info == null) {
			EventLoopGroup group = new NioEventLoopGroup();
			InetSocketAddress socketAddress = new InetSocketAddress(InetAddress.getByAddress(address), 53);
			Bootstrap b = new Bootstrap();
			b.group(group)
			.channel(NioDatagramChannel.class)
			.option(ChannelOption.SO_BROADCAST, true)
			.handler(new TransmissionInitializer());
			dnsServers.put(address, info = new DnsServerInfo(address, b.connect(socketAddress).sync().channel()));
		}
		return info;
	}

	public static void resolveQuery(DnsExchange resolver) throws InterruptedException, UnknownHostException {
		DnsServerInfo info = serverInfo(resolver.dnsAddress());
		synchronized (DnsClient.class) {
			info.setActiveCount(info.getActiveCount() + 1);
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
