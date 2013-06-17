package io.netty.handler.dns;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.Question;
import io.netty.handler.codec.dns.Resource;
import io.netty.handler.dns.decoder.record.MailExchangerRecord;
import io.netty.handler.dns.decoder.record.ServiceRecord;
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

	private static final EventExecutorGroup executor = new DefaultEventExecutorGroup(2);
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
					logger.warn("Failed to obtain system's DNS server addresses, using defaults only.", e);
				}
			}
		}.start();
	}

	private static boolean validAddress(byte[] address) {
		try {
			int id = obtainId();
			Channel channel = channelForAddress(address);
			Callable<List<ByteBuf>> callback = new DnsCallback<List<ByteBuf>>(-1, sendQuery(Resource.TYPE_A, "google.com", id, channel));
			return callback.call() != null;
		} catch (Exception e) {
			removeChannel(dnsServerChannels.get(address));
			return false;
		}
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

	private static int obtainId() {
		synchronized (idxLock) {
			return idx = (idx + 1) & 0xffff;
		}
	}

	private static DnsQuery sendQuery(int type, String domain, int id, Channel channel) throws InterruptedException {
		DnsQuery query = new DnsQuery(id);
		query.addQuestion(new Question(domain, type));
		channel.write(query).sync();
		return query;
	}

	public static boolean addDnsServer(byte[] dnsServerAddress) {
		return dnsServers.add(dnsServerAddress);
	}

	public static boolean removeDnsServer(byte[] dnsServerAddress) {
		return dnsServers.remove(dnsServerAddress);
	}

	public static byte[] getDnsServer(int index) {
		if (index > -1 && index < dnsServers.size()) {
			return dnsServers.get(index);
		}
		return null;
	}

	public static Channel channelForAddress(byte[] dnsServerAddress) throws UnknownHostException, SocketException, InterruptedException {
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
					.handler(new DnsClientInitializer());
					dnsServerChannels.put(dnsServerAddress, channel = b.connect().sync().channel());
				}
				return channel;
			}
		}
	}

	public static <T> Future<T> lookup(String domain) throws UnknownHostException, SocketException, InterruptedException {
		return resolveSingle(domain, dnsServers.get(0), Resource.TYPE_A, Resource.TYPE_AAAA);
	}

	public static <T> Future<List<T>> lookup(String domain, Integer family) throws UnknownHostException, SocketException, InterruptedException {
		if (family != null && family != 4 && family != 6) {
			throw new IllegalArgumentException("Family must be 4, 6, or null to indicate both 4 and 6.");
		}
		if (family == null) {
			return resolve(domain, dnsServers.get(0), Resource.TYPE_A, Resource.TYPE_AAAA);
		} else if (family == 4) {
			return resolve(domain, dnsServers.get(0), Resource.TYPE_A);
		}
		return resolve(domain, dnsServers.get(0), Resource.TYPE_AAAA);
	}

	public static <T> Future<T> resolveSingle(String domain, byte[] dnsServerAddress, int... types) throws UnknownHostException, SocketException, InterruptedException {
		Future<T> future = checkCache(domain, types);
		if (future != null) {
			return future;
		}
		int id = obtainId();
		Channel channel = channelForAddress(dnsServerAddress);
		DnsQuery[] queries = new DnsQuery[types.length];
		for (int i = 0; i < types.length; i++) {
			queries[i] = sendQuery(types[i], domain, id, channel);
		}
		return executor.submit(new SingleResultCallback<T>(new DnsCallback<List<T>>(dnsServers.indexOf(dnsServerAddress), queries)));
	}

	public static <T extends List<?>> Future<T> resolve(String domain, byte[] dnsServerAddress, int... types) throws UnknownHostException, SocketException, InterruptedException {
		Future<T> future = checkCache(domain, types);
		if (future != null) {
			return future;
		}
		int id = obtainId();
		Channel channel = channelForAddress(dnsServerAddress);
		DnsQuery[] queries = new DnsQuery[types.length];
		for (int i = 0; i < types.length; i++) {
			queries[i] = sendQuery(types[i], domain, id, channel);
		}
		return executor.submit(new DnsCallback<T>(dnsServers.indexOf(dnsServerAddress), queries));
	}

	public static Future<List<ByteBuf>> resolve4(String domain) throws UnknownHostException, SocketException, InterruptedException {
		return resolve(domain, dnsServers.get(0), Resource.TYPE_A);
	}

	public static Future<List<ByteBuf>> resolve6(String domain) throws UnknownHostException, SocketException, InterruptedException {
		return resolve(domain, dnsServers.get(0), Resource.TYPE_AAAA);
	}

	public static Future<List<MailExchangerRecord>> resolveMx(String domain) throws UnknownHostException, SocketException, InterruptedException {
		return resolve(domain, dnsServers.get(0), Resource.TYPE_MX);
	}

	public static Future<List<ServiceRecord>> resolveSrv(String domain) throws UnknownHostException, SocketException, InterruptedException {
		return resolve(domain, dnsServers.get(0), Resource.TYPE_SRV);
	}

	public static Future<List<List<String>>> resolveTxt(String domain) throws UnknownHostException, SocketException, InterruptedException {
		return resolve(domain, dnsServers.get(0), Resource.TYPE_TXT);
	}

	public static Future<List<String>> resolveCname(String domain) throws UnknownHostException, SocketException, InterruptedException {
		return resolve(domain, dnsServers.get(0), Resource.TYPE_CNAME);
	}

	public static Future<List<String>> resolveNs(String domain) throws UnknownHostException, SocketException, InterruptedException {
		return resolve(domain, dnsServers.get(0), Resource.TYPE_NS);
	}

	public static Future<List<String>> reverse(byte[] ipAddress) throws UnknownHostException, SocketException, InterruptedException {
		ByteBuf buf = Unpooled.copiedBuffer(ipAddress);
		Future<List<String>> future = reverse(buf);
		buf.release();
		return future;
	}

	public static Future<List<String>> reverse(ByteBuf ipAddress) throws UnknownHostException, SocketException, InterruptedException {
		int size = ipAddress.writerIndex() - ipAddress.readerIndex();
		StringBuilder domain = new StringBuilder();
		for (int i = size - 1; i > -1; i--) {
			domain.append(ipAddress.getUnsignedByte(i)).append(".");
		}
		return resolve(domain.append("in-addr.arpa").toString(), dnsServers.get(0), Resource.TYPE_PTR);
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
