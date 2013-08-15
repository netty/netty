/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.http;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class DefaultHttpHeaders extends HttpHeaders {

    private static final int BUCKET_SIZE = 17;

    // Pre calculate hash and index of often used header names
    private static final Map<String, HashIndex> HASH_MAPPINGS = new IdentityHashMap<String, HashIndex>();
    static {
        HASH_MAPPINGS.put(Names.ACCEPT, new HashIndex(Names.ACCEPT));
        HASH_MAPPINGS.put(Names.ACCEPT_CHARSET, new HashIndex(Names.ACCEPT_CHARSET));
        HASH_MAPPINGS.put(Names.ACCEPT_ENCODING, new HashIndex(Names.ACCEPT_ENCODING));
        HASH_MAPPINGS.put(Names.ACCEPT_LANGUAGE, new HashIndex(Names.ACCEPT_LANGUAGE));
        HASH_MAPPINGS.put(Names.ACCEPT_PATCH, new HashIndex(Names.ACCEPT_PATCH));
        HASH_MAPPINGS.put(Names.ACCEPT_RANGES, new HashIndex(Names.ACCEPT_RANGES));
        HASH_MAPPINGS.put(Names.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                new HashIndex(Names.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        HASH_MAPPINGS.put(Names.ACCESS_CONTROL_ALLOW_HEADERS, new HashIndex(Names.ACCESS_CONTROL_ALLOW_HEADERS));
        HASH_MAPPINGS.put(Names.ACCESS_CONTROL_ALLOW_METHODS, new HashIndex(Names.ACCESS_CONTROL_ALLOW_METHODS));
        HASH_MAPPINGS.put(Names.ACCESS_CONTROL_ALLOW_ORIGIN, new HashIndex(Names.ACCESS_CONTROL_ALLOW_ORIGIN));
        HASH_MAPPINGS.put(Names.ACCESS_CONTROL_EXPOSE_HEADERS, new HashIndex(Names.ACCESS_CONTROL_EXPOSE_HEADERS));
        HASH_MAPPINGS.put(Names.ACCESS_CONTROL_MAX_AGE, new HashIndex(Names.ACCESS_CONTROL_MAX_AGE));
        HASH_MAPPINGS.put(Names.ACCESS_CONTROL_REQUEST_HEADERS, new HashIndex(Names.ACCESS_CONTROL_REQUEST_HEADERS));
        HASH_MAPPINGS.put(Names.ACCESS_CONTROL_REQUEST_METHOD, new HashIndex(Names.ACCESS_CONTROL_REQUEST_METHOD));
        HASH_MAPPINGS.put(Names.AGE, new HashIndex(Names.AGE));
        HASH_MAPPINGS.put(Names.ALLOW, new HashIndex(Names.ALLOW));
        HASH_MAPPINGS.put(Names.AUTHORIZATION, new HashIndex(Names.AUTHORIZATION));
        HASH_MAPPINGS.put(Names.CACHE_CONTROL, new HashIndex(Names.CACHE_CONTROL));
        HASH_MAPPINGS.put(Names.CONNECTION, new HashIndex(Names.CONNECTION));
        HASH_MAPPINGS.put(Names.CONTENT_BASE, new HashIndex(Names.CONTENT_BASE));
        HASH_MAPPINGS.put(Names.CONTENT_ENCODING, new HashIndex(Names.CONTENT_ENCODING));
        HASH_MAPPINGS.put(Names.CONTENT_LANGUAGE, new HashIndex(Names.CONTENT_LANGUAGE));
        HASH_MAPPINGS.put(Names.CONTENT_MD5, new HashIndex(Names.CONTENT_MD5));
        HASH_MAPPINGS.put(Names.CONTENT_RANGE, new HashIndex(Names.CONTENT_RANGE));
        HASH_MAPPINGS.put(Names.CONTENT_TRANSFER_ENCODING, new HashIndex(Names.CONTENT_TRANSFER_ENCODING));
        HASH_MAPPINGS.put(Names.CONTENT_TYPE, new HashIndex(Names.CONTENT_TYPE));
        HASH_MAPPINGS.put(Names.COOKIE, new HashIndex(Names.COOKIE));
        HASH_MAPPINGS.put(Names.DATE, new HashIndex(Names.DATE));
        HASH_MAPPINGS.put(Names.ETAG, new HashIndex(Names.ETAG));
        HASH_MAPPINGS.put(Names.EXPECT, new HashIndex(Names.EXPECT));
        HASH_MAPPINGS.put(Names.EXPIRES, new HashIndex(Names.EXPIRES));
        HASH_MAPPINGS.put(Names.FROM, new HashIndex(Names.FROM));
        HASH_MAPPINGS.put(Names.HOST, new HashIndex(Names.HOST));
        HASH_MAPPINGS.put(Names.IF_MATCH, new HashIndex(Names.IF_MATCH));
        HASH_MAPPINGS.put(Names.IF_MODIFIED_SINCE, new HashIndex(Names.IF_MODIFIED_SINCE));
        HASH_MAPPINGS.put(Names.IF_NONE_MATCH, new HashIndex(Names.IF_NONE_MATCH));
        HASH_MAPPINGS.put(Names.IF_RANGE, new HashIndex(Names.IF_RANGE));
        HASH_MAPPINGS.put(Names.IF_UNMODIFIED_SINCE, new HashIndex(Names.IF_UNMODIFIED_SINCE));
        HASH_MAPPINGS.put(Names.LAST_MODIFIED, new HashIndex(Names.LAST_MODIFIED));
        HASH_MAPPINGS.put(Names.LOCATION, new HashIndex(Names.LOCATION));
        HASH_MAPPINGS.put(Names.MAX_FORWARDS, new HashIndex(Names.MAX_FORWARDS));
        HASH_MAPPINGS.put(Names.ORIGIN, new HashIndex(Names.ORIGIN));
        HASH_MAPPINGS.put(Names.PRAGMA, new HashIndex(Names.PRAGMA));
        HASH_MAPPINGS.put(Names.PROXY_AUTHENTICATE, new HashIndex(Names.PROXY_AUTHENTICATE));
        HASH_MAPPINGS.put(Names.PROXY_AUTHORIZATION, new HashIndex(Names.PROXY_AUTHORIZATION));
        HASH_MAPPINGS.put(Names.RANGE, new HashIndex(Names.RANGE));
        HASH_MAPPINGS.put(Names.REFERER, new HashIndex(Names.REFERER));
        HASH_MAPPINGS.put(Names.RETRY_AFTER, new HashIndex(Names.RETRY_AFTER));
        HASH_MAPPINGS.put(Names.SEC_WEBSOCKET_ACCEPT, new HashIndex(Names.SEC_WEBSOCKET_ACCEPT));
        HASH_MAPPINGS.put(Names.SEC_WEBSOCKET_KEY, new HashIndex(Names.SEC_WEBSOCKET_KEY));
        HASH_MAPPINGS.put(Names.SEC_WEBSOCKET_KEY1, new HashIndex(Names.SEC_WEBSOCKET_KEY1));
        HASH_MAPPINGS.put(Names.SEC_WEBSOCKET_KEY2, new HashIndex(Names.SEC_WEBSOCKET_KEY2));
        HASH_MAPPINGS.put(Names.SEC_WEBSOCKET_LOCATION, new HashIndex(Names.SEC_WEBSOCKET_LOCATION));
        HASH_MAPPINGS.put(Names.SEC_WEBSOCKET_ORIGIN, new HashIndex(Names.SEC_WEBSOCKET_ORIGIN));
        HASH_MAPPINGS.put(Names.SEC_WEBSOCKET_PROTOCOL, new HashIndex(Names.SEC_WEBSOCKET_PROTOCOL));
        HASH_MAPPINGS.put(Names.SEC_WEBSOCKET_VERSION, new HashIndex(Names.SEC_WEBSOCKET_VERSION));
        HASH_MAPPINGS.put(Names.SERVER, new HashIndex(Names.SERVER));
        HASH_MAPPINGS.put(Names.SET_COOKIE, new HashIndex(Names.SET_COOKIE));
        HASH_MAPPINGS.put(Names.SET_COOKIE2, new HashIndex(Names.SET_COOKIE2));
        HASH_MAPPINGS.put(Names.TE, new HashIndex(Names.TE));
        HASH_MAPPINGS.put(Names.TRAILER, new HashIndex(Names.TRAILER));
        HASH_MAPPINGS.put(Names.TRANSFER_ENCODING, new HashIndex(Names.TRANSFER_ENCODING));
        HASH_MAPPINGS.put(Names.UPGRADE, new HashIndex(Names.UPGRADE));
        HASH_MAPPINGS.put(Names.USER_AGENT, new HashIndex(Names.USER_AGENT));
        HASH_MAPPINGS.put(Names.VARY, new HashIndex(Names.VARY));
        HASH_MAPPINGS.put(Names.VIA, new HashIndex(Names.VIA));
        HASH_MAPPINGS.put(Names.WARNING, new HashIndex(Names.WARNING));
        HASH_MAPPINGS.put(Names.WEBSOCKET_LOCATION, new HashIndex(Names.WEBSOCKET_LOCATION));
        HASH_MAPPINGS.put(Names.WEBSOCKET_ORIGIN, new HashIndex(Names.WEBSOCKET_ORIGIN));
        HASH_MAPPINGS.put(Names.WEBSOCKET_PROTOCOL, new HashIndex(Names.WEBSOCKET_PROTOCOL));
        HASH_MAPPINGS.put(Names.WWW_AUTHENTICATE, new HashIndex(Names.WWW_AUTHENTICATE));
    }

    private static int hash(String name) {
        int h = 0;
        for (int i = name.length() - 1; i >= 0; i --) {
            char c = name.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                c += 32;
            }
            h = 31 * h + c;
        }

        if (h > 0) {
            return h;
        } else if (h == Integer.MIN_VALUE) {
            return Integer.MAX_VALUE;
        } else {
            return -h;
        }
    }

    private static boolean eq(String name1, String name2) {
        // if the strings are equal there is not need to compare char against char
        if (name1 == name2) {
            return true;
        }

        int nameLen = name1.length();
        if (nameLen != name2.length()) {
            return false;
        }

        for (int i = nameLen - 1; i >= 0; i --) {
            char c1 = name1.charAt(i);
            char c2 = name2.charAt(i);
            if (c1 != c2) {
                if (c1 >= 'A' && c1 <= 'Z') {
                    c1 += 32;
                }
                if (c2 >= 'A' && c2 <= 'Z') {
                    c2 += 32;
                }
                if (c1 != c2) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int index(int hash) {
        return hash % BUCKET_SIZE;
    }

    private final HeaderEntry[] entries = new HeaderEntry[BUCKET_SIZE];
    private final HeaderEntry head = new HeaderEntry(-1, null, null);

    public DefaultHttpHeaders() {
        head.before = head.after = head;
    }

    void validateHeaderName0(String headerName) {
        validateHeaderName(headerName);
    }

    @Override
    public HttpHeaders add(final String name, final Object value) {
        int h;
        int i;
        HashIndex index = HASH_MAPPINGS.get(name);
        if (index == null) {
            validateHeaderName0(name);
            h = hash(name);
            i = index(h);
        } else {
            h = index.hash;
            i = index.index;
        }

        String strVal = toString(value);
        validateHeaderValue(strVal);
        add0(h, i, name, strVal);
        return this;
    }

    @Override
    public HttpHeaders add(String name, Iterable<?> values) {
        int h;
        int i;
        HashIndex index = HASH_MAPPINGS.get(name);
        if (index == null) {
            validateHeaderName0(name);
            h = hash(name);
            i = index(h);
        } else {
            h = index.hash;
            i = index.index;
        }

        for (Object v: values) {
            String vstr = toString(v);
            validateHeaderValue(vstr);
            add0(h, i, name, vstr);
        }
        return this;
    }

    private void add0(int h, int i, final String name, final String value) {
        // Update the hash table.
        HeaderEntry e = entries[i];
        HeaderEntry newEntry;
        entries[i] = newEntry = new HeaderEntry(h, name, value);
        newEntry.next = e;

        // Update the linked list.
        newEntry.addBefore(head);
    }

    @Override
    public HttpHeaders remove(final String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        int h;
        int i;
        HashIndex index = HASH_MAPPINGS.get(name);
        if (index == null) {
            h = hash(name);
            i = index(h);
        } else {
            h = index.hash;
            i = index.index;
        }

        remove0(h, i, name);
        return this;
    }

    private void remove0(int h, int i, String name) {
        HeaderEntry e = entries[i];
        if (e == null) {
            return;
        }

        for (;;) {
            if (e.hash == h && eq(name, e.key)) {
                e.remove();
                HeaderEntry next = e.next;
                if (next != null) {
                    entries[i] = next;
                    e = next;
                } else {
                    entries[i] = null;
                    return;
                }
            } else {
                break;
            }
        }

        for (;;) {
            HeaderEntry next = e.next;
            if (next == null) {
                break;
            }
            if (next.hash == h && eq(name, next.key)) {
                e.next = next.next;
                next.remove();
            } else {
                e = next;
            }
        }
    }

    @Override
    public HttpHeaders set(final String name, final Object value) {
        int h;
        int i;
        HashIndex index = HASH_MAPPINGS.get(name);
        if (index == null) {
            validateHeaderName0(name);

            h = hash(name);
            i = index(h);
        } else {
            h = index.hash;
            i = index.index;
        }

        String strVal = toString(value);
        validateHeaderValue(strVal);

        remove0(h, i, name);
        add0(h, i, name, strVal);
        return this;
    }

    @Override
    public HttpHeaders set(final String name, final Iterable<?> values) {
        if (values == null) {
            throw new NullPointerException("values");
        }

        int h;
        int i;
        HashIndex index = HASH_MAPPINGS.get(name);
        if (index == null) {
            validateHeaderName0(name);

            h = hash(name);
            i = index(h);
        } else {
            h = index.hash;
            i = index.index;
        }

        remove0(h, i, name);
        for (Object v: values) {
            if (v == null) {
                break;
            }
            String strVal = toString(v);
            validateHeaderValue(strVal);
            add0(h, i, name, strVal);
        }

        return this;
    }

    @Override
    public HttpHeaders clear() {
        Arrays.fill(entries, null);
        head.before = head.after = head;
        return this;
    }

    @Override
    public String get(final String name) {
        return get(name, false);
    }

    private String get(final String name, boolean last) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        int h;
        int i;
        HashIndex index = HASH_MAPPINGS.get(name);
        if (index == null) {
            h = hash(name);
            i = index(h);
        } else {
            h = index.hash;
            i = index.index;
        }

        HeaderEntry e = entries[i];
        String value = null;
        // loop until the first header was found
        while (e != null) {
            if (e.hash == h && eq(name, e.key)) {
                value = e.value;
                if (last) {
                    break;
                }
            }

            e = e.next;
        }
        return value;
    }

    @Override
    public List<String> getAll(final String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }

        LinkedList<String> values = new LinkedList<String>();

        int h;
        int i;
        HashIndex index = HASH_MAPPINGS.get(name);
        if (index == null) {
            h = hash(name);
            i = index(h);
        } else {
            h = index.hash;
            i = index.index;
        }

        HeaderEntry e = entries[i];
        while (e != null) {
            if (e.hash == h && eq(name, e.key)) {
                values.addFirst(e.value);
            }
            e = e.next;
        }
        return values;
    }

    @Override
    public List<Map.Entry<String, String>> entries() {
        List<Map.Entry<String, String>> all =
            new LinkedList<Map.Entry<String, String>>();

        HeaderEntry e = head.after;
        while (e != head) {
            all.add(e);
            e = e.after;
        }
        return all;
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return entries().iterator();
    }

    @Override
    public boolean contains(String name) {
        return get(name, true) != null;
    }

    @Override
    public boolean isEmpty() {
        return head == head.after;
    }

    @Override
    public Set<String> names() {

        Set<String> names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);

        HeaderEntry e = head.after;
        while (e != head) {
            names.add(e.key);
            e = e.after;
        }
        return names;
    }

    private static String toString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Date) {
            return HttpHeaderDateFormat.get().format((Date) value);
        }
        if (value instanceof Calendar) {
            return HttpHeaderDateFormat.get().format(((Calendar) value).getTime());
        }
        return value.toString();
    }

    private static final class HeaderEntry implements Map.Entry<String, String> {
        final int hash;
        final String key;
        String value;
        HeaderEntry next;
        HeaderEntry before, after;

        HeaderEntry(int hash, String key, String value) {
            this.hash = hash;
            this.key = key;
            this.value = value;
        }

        void remove() {
            before.after = after;
            after.before = before;
        }

        void addBefore(HeaderEntry e) {
            after  = e;
            before = e.before;
            before.after = this;
            after.before = this;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public String setValue(String value) {
            if (value == null) {
                throw new NullPointerException("value");
            }
            validateHeaderValue(value);
            String oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        @Override
        public String toString() {
            return key + '=' + value;
        }
    }

    private static final class HashIndex {
        final int hash;
        final int index;

        HashIndex(String name) {
            hash = hash(name);
            index = index(hash);
        }
    }
}
