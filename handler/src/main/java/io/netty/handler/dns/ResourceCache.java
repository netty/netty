package io.netty.handler.dns;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ResourceCache {

	private static final Map<String, Map<Integer, Set<Record<?>>>> recordCache = new HashMap<String, Map<Integer, Set<Record<?>>>>();

	@SuppressWarnings("unchecked")
	public static <T> T getRecord(String name, int type) {
		List<?> records = getRecords(name, type);
		if (records == null)
			return null;
		return records.size() == 0 ? null : (T) records.get(0);
	}

	@SuppressWarnings("unchecked")
	public static <T> List<T> getRecords(String name, int type) {
		Map<Integer, Set<Record<?>>> records = recordCache.get(name);
		if (records == null || records.isEmpty()) {
			return null;
		}
		Set<Record<?>> subresults = records.get(type);
		if (subresults == null || subresults.isEmpty()) {
			return null;
		}
		List<T> results = new ArrayList<T>();
		synchronized (subresults) {
			for (Iterator<Record<?>> iter = subresults.iterator(); iter.hasNext(); ) {
				Record<?> record = iter.next();
				if (System.currentTimeMillis() > record.expiration) {
					iter.remove();
				} else {
					results.add((T) record.content());
				}
			}
		}
		if (results.isEmpty()) {
			return null;
		}
		return results;
	}

	public static <T> void submitRecord(String name, int type, long ttl, T content) {
		Map<Integer, Set<Record<?>>> records = recordCache.get(name);
		if (records == null) {
			recordCache.put(name, records = new HashMap<Integer, Set<Record<?>>>());
		}
		Set<Record<?>> results = records.get(type);
		if (results == null) {
			records.put(type, results = new HashSet<Record<?>>());
		}
		synchronized (results) {
			results.add(new Record<T>(content, ttl));
		}
	}

	static class Record<T> {

		private final T content;
		private final long expiration;

		public Record(T content, long ttl) {
			this.content = content;
			expiration = System.currentTimeMillis() + ttl * 1000l;
		}

		public long expiration() {
			return expiration;
		}

		public T content() {
			return content;
		}

		@Override
		public boolean equals(Object other) {
			if (other == this) {
				return true;
			}
			if (other instanceof Record) {
				Record<?> oRec = (Record<?>) other;
				if (oRec.content == content) {
					return true;
				}
				return oRec.content.equals(content);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return content.getClass().hashCode();
		}
	}
}
