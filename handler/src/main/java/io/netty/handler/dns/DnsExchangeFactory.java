package io.netty.handler.dns;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.Question;
import io.netty.handler.codec.dns.Resource;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;

public class DnsExchangeFactory {

	public static final byte[] defaultDnsServer = {8, 8, 4, 4};

	private static final EventExecutorGroup executor = new DefaultEventExecutorGroup(1);
	private static final Map<byte[], Channel> dnsServerChannels = new HashMap<byte[], Channel>();
	private static final Queue<Short> ids = new LinkedBlockingDeque<Short>(65536);
	static {
		for (int i = 0; i < 65536; i++) {
			ids.add((short) i);
		}
	}

	private static int obtainId() {
		Short temp = ids.poll();
		if (temp == null) {
			throw new RuntimeException("Too many concurrent DNS requests occuring.");
		}
		return temp & 0xffff;
	}

	private static void resolve4_(String domain, int id, Channel channel) {
		DnsQuery query = new DnsQuery(id);
		query.addQuestion(new Question(domain, Resource.TYPE_A));
		channel.write(query);
	}

	private static void resolve6_(String domain, int id, Channel channel) {
		DnsQuery query = new DnsQuery(id);
		query.addQuestion(new Question(domain, Resource.TYPE_AAAA));
		channel.write(query);
	}

	public static Channel channelForAddress(byte[] dnsServerAddress) throws UnknownHostException, InterruptedException {
		Channel channel = null;
		if ((channel = dnsServerChannels.get(dnsServerAddress)) != null) {
			return channel;
		} else {
			synchronized (dnsServerChannels) {
				if ((channel = dnsServerChannels.get(dnsServerAddress)) == null) {
					EventLoopGroup group = new NioEventLoopGroup();
					InetSocketAddress socketAddress = new InetSocketAddress(InetAddress.getByAddress(dnsServerAddress), 53);
					Bootstrap b = new Bootstrap();
					b.group(group)
						.channel(NioDatagramChannel.class)
						.option(ChannelOption.SO_SNDBUF, 1048576)
						.option(ChannelOption.SO_RCVBUF, 1048576)
						.option(ChannelOption.UDP_RECEIVE_PACKET_SIZE, 512)
						.handler(new DnsClientInitializer());
					dnsServerChannels.put(dnsServerAddress, channel = b.connect(socketAddress).sync().channel());
				}
				return channel;
			}
		}
	}

	public static Future<ByteBuf> lookup(String domain) throws UnknownHostException, InterruptedException {
		return lookup(domain, null);
	}

	public static Future<ByteBuf> lookup(String domain, Integer family) throws UnknownHostException, InterruptedException {
		if (family != null && family != 4 && family != 6) {
			throw new IllegalArgumentException("Family must be 4, 6, or null to indicate both 4 and 6.");
		}
		int id = obtainId();
		Channel channel = channelForAddress(defaultDnsServer);
		if (family == null) {
			resolve4_(domain, id, channel);
			resolve6_(domain, id, channel);
		} else if (family == 4) {
			resolve4_(domain, id, channel);
		} else if (family == 6) {
			resolve6_(domain, id, channel);
		}
		return executor.submit(new DnsCallback<ByteBuf>(id, Resource.TYPE_A, Resource.TYPE_AAAA));
	}

	public static Future<ByteBuf> resolve4(String domain) throws UnknownHostException, InterruptedException {
		int id = obtainId();
		Channel channel = channelForAddress(defaultDnsServer);
		resolve4_(domain, id, channel);
		return executor.submit(new DnsCallback<ByteBuf>(id, Resource.TYPE_A));
	}

	public static Future<ByteBuf> resolve6(String domain) throws UnknownHostException, InterruptedException {
		int id = obtainId();
		Channel channel = channelForAddress(defaultDnsServer);
		resolve6_(domain, id, channel);
		return executor.submit(new DnsCallback<ByteBuf>(id, Resource.TYPE_AAAA));
	}
	public static void removeChannel(Channel channel) {
		synchronized (dnsServerChannels) {
			for (Iterator<Map.Entry<byte[], Channel>> iter = dnsServerChannels.entrySet().iterator(); iter.hasNext(); ) {
				Map.Entry<byte[], Channel> entry = iter.next();
				if (entry.getValue() == channel) {
					if (channel.isOpen()) {
						try {
							channel.close();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					iter.remove();
					break;
				}
			}
		}
	}

	private DnsExchangeFactory() {
	}

}
