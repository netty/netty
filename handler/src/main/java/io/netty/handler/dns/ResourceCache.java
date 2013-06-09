package io.netty.handler.dns;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ResourceCache {

	private static final Map<String, Map<Integer, List<Record<?>>>> recordCache = new HashMap<String, Map<Integer, List<Record<?>>>>();

	@SuppressWarnings("unchecked")
	public static <E> E getRecord(String name, int type) {
		List<?> records = getRecords(name, type);
		return records.size() == 0 ? null : (E) records.get(0);
	}

	@SuppressWarnings("unchecked")
	public static <E> List<E> getRecords(String name, int type) {
		Map<Integer, List<Record<?>>> records = recordCache.get(name);
		List<E> results = new ArrayList<E>();
		if (records == null || records.isEmpty()) {
			return results;
		}
		List<Record<?>> subresults = records.get(type);
		if (subresults == null || subresults.isEmpty()) {
			return results;
		}
		for (Iterator<Record<?>> iter = subresults.iterator(); iter.hasNext(); ) {
			Record<?> record = iter.next();
			if (System.currentTimeMillis() > record.expiration) {
				iter.remove();
			} else {
				results.add((E) record.content());
			}
		}
		return results;
	}

	static int count = 0;

	public static <E> void submitRecord(String name, int type, long ttl, E content) {
		Map<Integer, List<Record<?>>> records = recordCache.get(name);
		if (records == null) {
			recordCache.put(name, records = new HashMap<Integer, List<Record<?>>>());
		}
		List<Record<?>> results = records.get(type);
		if (results == null) {
			records.put(type, results = new ArrayList<Record<?>>());
		}
		results.add(new Record<E>(content, ttl));
	}

	static class Record<E> {

		private final E content;
		private final long expiration;

		public Record(E content, long ttl) {
			this.content = content;
			expiration = System.currentTimeMillis() + ttl * 1000l;
		}

		public long expiration() {
			return expiration;
		}

		public E content() {
			return content;
		}
	}
}
