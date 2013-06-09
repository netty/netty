package io.netty.handler.dns;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.Question;
import io.netty.handler.codec.dns.Resource;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class DnsExchangeFactory {

	public static final long REQUEST_TIMEOUT = 2000;

	private static final EventExecutorGroup executor = new DefaultEventExecutorGroup(4);
	private static final InternalLogger logger = InternalLoggerFactory.getInstance(DnsExchangeFactory.class);
	private static final List<byte[]> dnsServers = new ArrayList<byte[]>();
	private static final Map<byte[], Channel> dnsServerChannels = new HashMap<byte[], Channel>();
	private static final Object idxLock = new Object();

	private static int idx = 0;

	static {
		dnsServers.add(new byte[] {8, 8, 8, 8}); // Google DNS servers
		dnsServers.add(new byte[] {8, 8, 4, 4});
		dnsServers.add(new byte[] {-48, 67, -34, -34}); // OpenDNS servers
		dnsServers.add(new byte[] {-48, 67, -36, -36});
		new Thread() {
			{
				setDaemon(true);
				setPriority(Thread.MIN_PRIORITY);
			}
			@Override
			public void run() {
				try {
					Class<?> configClass = Class.forName("sun.net.dns.ResolverConfiguration");
					Method open = configClass.getMethod("open");
					Method nameservers = configClass.getMethod("nameservers");
					Object instance = open.invoke(null);
					@SuppressWarnings("unchecked")
					List<String> list = (List<String>) nameservers.invoke(instance);
					for (String dns : list) {
						String[] parts = dns.split("\\.");
						if (parts.length == 4 || parts.length == 16) {
							byte[] address = new byte[parts.length];
							for (int i = 0; i < address.length; i++) {
								address[i] = (byte) Integer.parseInt(parts[i]);
							}
							if (validAddress(address)) {
								dnsServers.add(address);
							}
						}
					}
				} catch (Exception e) {
					logger.warn("Failed to obtain system's DNS server addresses, using defaults.", e);
				}
			}
		}.start();
	}

	private static <E> Future<E> checkCache(String name, int... types) {
		for (int i = 0; i < types.length; i++) {
			E record = ResourceCache.getRecord(name, types[i]);
			if (record != null) {
				return new CachedFuture<E>(record);
			}
		}
		return null;
	}

	private static Future<ByteBuf> lookup_(String domain, Integer family, byte[] dnsServerAddress) throws UnknownHostException, InterruptedException, SocketException {
		if (family != null && family != 4 && family != 6) {
			throw new IllegalArgumentException("Family must be 4, 6, or null to indicate both 4 and 6.");
		}
		Future<ByteBuf> future = null;
		if (family == null) {
			future = checkCache(domain, Resource.TYPE_A, Resource.TYPE_AAAA);
		} else if (family == 4) {
			future = checkCache(domain, Resource.TYPE_A);
		} else if (family == 6) {
			future = checkCache(domain, Resource.TYPE_AAAA);
		}
		if (future != null) {
			return future;
		}
		int id = obtainId();
		Channel channel = channelForAddress(dnsServerAddress);
		if (family == null) {
			sendQuery4_(domain, id, channel);
			sendQuery6_(domain, id, channel);
		} else if (family == 4) {
			sendQuery4_(domain, id, channel);
		} else if (family == 6) {
			sendQuery6_(domain, id, channel);
		}
		return executor.submit(new DnsCallback<ByteBuf>(id, Resource.TYPE_A, Resource.TYPE_AAAA));
	}

	private static Future<ByteBuf> resolve4_(String domain, byte[] dnsServerAddress) throws UnknownHostException, SocketException, InterruptedException {
		Future<ByteBuf> future = checkCache(domain, Resource.TYPE_A);
		if (future != null) {
			return future;
		}
		int id = obtainId();
		Channel channel = channelForAddress(dnsServerAddress);
		sendQuery4_(domain, id, channel);
		return executor.submit(new DnsCallback<ByteBuf>(id, Resource.TYPE_A));
	}

	private static Future<ByteBuf> resolve6_(String domain, byte[] dnsServerAddress) throws UnknownHostException, SocketException, InterruptedException {
		Future<ByteBuf> future = checkCache(domain, Resource.TYPE_AAAA);
		if (future != null) {
			return future;
		}
		int id = obtainId();
		Channel channel = channelForAddress(dnsServerAddress);
		sendQuery6_(domain, id, channel);
		return executor.submit(new DnsCallback<ByteBuf>(id, Resource.TYPE_A));
	}

	private static int obtainId() {
		synchronized (idxLock) {
			return idx = (idx + 1) & 0xffff;
		}
	}

	private static void sendQuery4_(String domain, int id, Channel channel) throws InterruptedException {
		DnsQuery query = new DnsQuery(id);
		query.addQuestion(new Question(domain, Resource.TYPE_A));
		channel.write(query).sync();
	}

	private static void sendQuery6_(String domain, int id, Channel channel) throws InterruptedException {
		DnsQuery query = new DnsQuery(id);
		query.addQuestion(new Question(domain, Resource.TYPE_AAAA));
		channel.write(query).sync();
	}

	private static boolean validAddress(byte[] address) {
		try {
			int id = obtainId();
			Channel channel = channelForAddress(address);
			sendQuery4_("google.com", id, channel);
			sendQuery6_("google.com", id, channel);
			Callable<ByteBuf> callback = new DnsCallback<ByteBuf>(id, Resource.TYPE_A, Resource.TYPE_AAAA);
			return callback.call() != null;
		} catch (Exception e) {
			removeChannel(dnsServerChannels.get(address));
			return false;
		}
	}

	public static Channel channelForAddress(byte[] dnsServerAddress) throws UnknownHostException, InterruptedException, SocketException {
		Channel channel = null;
		if ((channel = dnsServerChannels.get(dnsServerAddress)) != null) {
			return channel;
		} else {
			synchronized (dnsServerChannels) {
				if ((channel = dnsServerChannels.get(dnsServerAddress)) == null) {
					InetAddress address = InetAddress.getByAddress(dnsServerAddress);
					Bootstrap b = new Bootstrap();
					b.group(new NioEventLoopGroup())
					.channel(NioDatagramChannel.class)
					.remoteAddress(address, 53)
					.option(ChannelOption.SO_BROADCAST, true)
					.option(ChannelOption.SO_SNDBUF, 1048576)
					.option(ChannelOption.SO_RCVBUF, 1048576)
					.option(ChannelOption.UDP_RECEIVE_PACKET_SIZE, 512)
					.handler(new DnsClientInitializer());
					dnsServerChannels.put(dnsServerAddress, channel = b.connect().sync().channel());
				}
				return channel;
			}
		}
	}

	public static Future<ByteBuf> lookup(String domain) throws UnknownHostException, InterruptedException, SocketException {
		return lookup(domain, null);
	}

	public static Future<ByteBuf> lookup(String domain, Integer family) throws UnknownHostException, InterruptedException, SocketException {
		return lookup_(domain, null, dnsServers.get(0));
	}

	public static Future<ByteBuf> resolve4(String domain) throws UnknownHostException, InterruptedException, SocketException {
		return resolve4_(domain, dnsServers.get(0));
	}

	public static Future<ByteBuf> resolve6(String domain) throws UnknownHostException, InterruptedException, SocketException {
		return resolve6_(domain, dnsServers.get(0));
	}

	public static void removeChannel(Channel channel) {
		synchronized (dnsServerChannels) {
			for (Iterator<Map.Entry<byte[], Channel>> iter = dnsServerChannels.entrySet().iterator(); iter.hasNext(); ) {
				Map.Entry<byte[], Channel> entry = iter.next();
				if (entry.getValue() == channel) {
					if (channel.isOpen()) {
						try {
							channel.close().sync();
						} catch (Exception e) {
							e.printStackTrace();
						} finally {
							channel.eventLoop().shutdownGracefully();
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
