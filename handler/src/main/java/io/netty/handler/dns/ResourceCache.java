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
		if (records == null) {
			return results;
		}
		if (records.isEmpty()) {
			return results;
		}
		for (Iterator<Map.Entry<Integer, List<Record<?>>>> iter = records.entrySet().iterator(); iter.hasNext(); ) {
			Map.Entry<Integer, List<Record<?>>> entry = iter.next();
			if (type == entry.getKey()) {
				List<Record<?>> subresults = entry.getValue();
				if (subresults.isEmpty())
					break;
				for (Iterator<Record<?>> recIt = subresults.iterator(); recIt.hasNext(); ) {
					Record<?> record = recIt.next();
					if (System.currentTimeMillis() > record.expiration) {
						recIt.remove();
					}
					results.add((E) record.content());
				}
				break;
			}
		}
		return results;
	}

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
			expiration = System.currentTimeMillis() + ttl;
		}

		public long expiration() {
			return expiration;
		}

		public E content() {
			return content;
		}
	}
}
