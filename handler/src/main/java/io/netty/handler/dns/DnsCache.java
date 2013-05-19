package io.netty.handler.dns;

import java.util.HashMap;
import java.util.Map;

public class DnsCache {

	private static final Map<String, Answer> cache = new HashMap<String, Answer>();

	public static synchronized byte[] obtainAnswerData(String name) {
		Answer answer = cache.get(name);
		if (answer == null || System.currentTimeMillis() > answer.expiration())
			return null;
		return answer.data();
	}

	public static synchronized void submitAnswer(String name, byte[] data, long ttl) {
		cache.put(name, new Answer(data, ttl));
	}

}
