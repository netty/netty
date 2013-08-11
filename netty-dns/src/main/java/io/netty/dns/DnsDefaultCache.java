/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.dns;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles the caching of all resource records for DNS queries.
 */
public final class DnsDefaultCache implements DnsCachingStrategy {

    private final Map<String, Map<Integer, Set<Record<?>>>> recordCache = new HashMap<String, Map<Integer, Set<Record<?>>>>();

    /**
     * Returns a <strong>single</strong> record for the given domain {@code name} and record {@code type}, or null if
     * this record does not exist.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getRecord(String name, int type) {
        List<?> records = getRecords(name, type);
        if (records == null) {
            return null;
        }
        return records.size() == 0 ? null : (T) records.get(0);
    }

    /**
     * Returns a <strong>{@code List}</strong> of records for the given domain {@code name} and record {@code type}, or
     * null if no records exist.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> getRecords(String name, int type) {
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
            for (Iterator<Record<?>> iter = subresults.iterator(); iter.hasNext();) {
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

    /**
     * Submits a record to the cache.
     *
     * @param name
     *            the domain name for the record
     * @param type
     *            the type of record
     * @param ttl
     *            the time to live for the record
     * @param content
     *            the record (i.e. for A records, this would be a {@link ByteBuf})
     */
    @Override
    public <T> void submitRecord(String name, int type, long ttl, T content) {
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

    /**
     * Represents a single resource record.
     *
     * @param <T>
     *            the type of record (i.e. for A records, this would be {@link ByteBuf})
     */
    class Record<T> {

        private final T content;
        private final long expiration;

        /**
         * Constructs the resource record.
         *
         * @param content
         *            the content of the record
         * @param ttl
         *            the time to live for the record
         */
        public Record(T content, long ttl) {
            this.content = content;
            expiration = System.currentTimeMillis() + ttl * 1000L;
        }

        /**
         * Returns when this record will expire in milliseconds, based on the machine's time.
         */
        public long expiration() {
            return expiration;
        }

        /**
         * Returns the content of this record.
         */
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

        // Don't want to store duplicate records
        @Override
        public int hashCode() {
            return content.getClass().hashCode();
        }
    }
}
