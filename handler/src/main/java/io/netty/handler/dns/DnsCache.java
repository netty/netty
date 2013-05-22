package bakkar.mohamed.dnsresolver;

import io.netty.buffer.ByteBuf;

import java.util.HashMap;
import java.util.Map;

public class DnsCache {

	private static final Map<String, Answer> cache = new HashMap<String, Answer>();

	public static ByteBuf obtainAnswerData(String name, int type) {
		Answer answer = null;
		synchronized (DnsCache.class) {
			answer = cache.get(name + type);
		}
		if (answer == null || System.currentTimeMillis() > answer.expiration())
			return null;
		return answer.content();
	}

	public static synchronized void submitAnswer(String name, int type, ByteBuf content, long ttl) {
		cache.put(name + type, new Answer(content, ttl));
	}

}
