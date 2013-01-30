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
package io.netty.handler.codec.spdy;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Provides the constants for the standard SPDY HTTP header names and commonly
 * used utility methods that access a {@link SpdyHeaderBlock}.
 * @apiviz.stereotype static
 */
public class SpdyHeaders {

    /**
     * SPDY HTTP header names
     * @apiviz.stereotype static
     */
    public static final class HttpNames {
        /**
         * {@code ":host"}
         */
        public static final String HOST = ":host";
        /**
         * {@code ":getMethod"}
         */
        public static final String METHOD = ":getMethod";
        /**
         * {@code ":path"}
         */
        public static final String PATH = ":path";
        /**
         * {@code ":scheme"}
         */
        public static final String SCHEME = ":scheme";
        /**
         * {@code ":getStatus"}
         */
        public static final String STATUS = ":getStatus";
        /**
         * {@code ":version"}
         */
        public static final String VERSION = ":version";

        private HttpNames() { }
    }

    /**
     * SPDY/2 HTTP header names
     * @apiviz.stereotype static
     */
    public static final class Spdy2HttpNames {
        /**
         * {@code "getMethod"}
         */
        public static final String METHOD = "getMethod";
        /**
         * {@code "scheme"}
         */
        public static final String SCHEME = "scheme";
        /**
         * {@code "getStatus"}
         */
        public static final String STATUS = "getStatus";
        /**
         * {@code "url"}
         */
        public static final String URL = "url";
        /**
         * {@code "version"}
         */
        public static final String VERSION = "version";

        private Spdy2HttpNames() { }
    }

    /**
     * Returns the header value with the specified header name.  If there are
     * more than one header value for the specified header name, the first
     * value is returned.
     *
     * @return the header value or {@code null} if there is no such header
     */
    public static String getHeader(SpdyHeaderBlock block, String name) {
        return block.getHeader(name);
    }

    /**
     * Returns the header value with the specified header name.  If there are
     * more than one header value for the specified header name, the first
     * value is returned.
     *
     * @return the header value or the {@code defaultValue} if there is no such
     *         header
     */
    public static String getHeader(SpdyHeaderBlock block, String name, String defaultValue) {
        String value = block.getHeader(name);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    /**
     * Sets a new header with the specified name and value.  If there is an
     * existing header with the same name, the existing header is removed.
     */
    public static void setHeader(SpdyHeaderBlock block, String name, Object value) {
        block.setHeader(name, value);
    }

    /**
     * Sets a new header with the specified name and values.  If there is an
     * existing header with the same name, the existing header is removed.
     */
    public static void setHeader(SpdyHeaderBlock block, String name, Iterable<?> values) {
        block.setHeader(name, values);
    }

    /**
     * Adds a new header with the specified name and value.
     */
    public static void addHeader(SpdyHeaderBlock block, String name, Object value) {
        block.addHeader(name, value);
    }

    /**
     * Removes the SPDY host header.
     */
    public static void removeHost(SpdyHeaderBlock block) {
        block.removeHeader(HttpNames.HOST);
    }

    /**
     * Returns the SPDY host header.
     */
    public static String getHost(SpdyHeaderBlock block) {
        return block.getHeader(HttpNames.HOST);
    }

    /**
     * Set the SPDY host header.
     */
    public static void setHost(SpdyHeaderBlock block, String host) {
        block.setHeader(HttpNames.HOST, host);
    }

    /**
     * Removes the HTTP getMethod header.
     */
    public static void removeMethod(int spdyVersion, SpdyHeaderBlock block) {
        if (spdyVersion < 3) {
            block.removeHeader(Spdy2HttpNames.METHOD);
        } else {
            block.removeHeader(HttpNames.METHOD);
        }
    }

    /**
     * Returns the {@link HttpMethod} represented by the HTTP getMethod header.
     */
    public static HttpMethod getMethod(int spdyVersion, SpdyHeaderBlock block) {
        try {
            if (spdyVersion < 3) {
                return HttpMethod.valueOf(block.getHeader(Spdy2HttpNames.METHOD));
            } else {
                return HttpMethod.valueOf(block.getHeader(HttpNames.METHOD));
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Sets the HTTP getMethod header.
     */
    public static void setMethod(int spdyVersion, SpdyHeaderBlock block, HttpMethod method) {
        if (spdyVersion < 3) {
            block.setHeader(Spdy2HttpNames.METHOD, method.name());
        } else {
            block.setHeader(HttpNames.METHOD, method.name());
        }
    }

    /**
     * Removes the URL scheme header.
     */
    public static void removeScheme(int spdyVersion, SpdyHeaderBlock block) {
        if (spdyVersion < 2) {
            block.removeHeader(Spdy2HttpNames.SCHEME);
        } else {
            block.removeHeader(HttpNames.SCHEME);
        }
    }

    /**
     * Returns the value of the URL scheme header.
     */
    public static String getScheme(int spdyVersion, SpdyHeaderBlock block) {
        if (spdyVersion < 3) {
            return block.getHeader(Spdy2HttpNames.SCHEME);
        } else {
            return block.getHeader(HttpNames.SCHEME);
        }
    }

    /**
     * Sets the URL scheme header.
     */
    public static void setScheme(int spdyVersion, SpdyHeaderBlock block, String scheme) {
        if (spdyVersion < 3) {
            block.setHeader(Spdy2HttpNames.SCHEME, scheme);
        } else {
            block.setHeader(HttpNames.SCHEME, scheme);
        }
    }

    /**
     * Removes the HTTP response getStatus header.
     */
    public static void removeStatus(int spdyVersion, SpdyHeaderBlock block) {
        if (spdyVersion < 3) {
            block.removeHeader(Spdy2HttpNames.STATUS);
        } else {
            block.removeHeader(HttpNames.STATUS);
        }
    }

    /**
     * Returns the {@link HttpResponseStatus} represented by the HTTP response getStatus header.
     */
    public static HttpResponseStatus getStatus(int spdyVersion, SpdyHeaderBlock block) {
        try {
            String status;
            if (spdyVersion < 3) {
                status = block.getHeader(Spdy2HttpNames.STATUS);
            } else {
                status = block.getHeader(HttpNames.STATUS);
            }
            int space = status.indexOf(' ');
            if (space == -1) {
                return HttpResponseStatus.valueOf(Integer.parseInt(status));
            } else {
                int code = Integer.parseInt(status.substring(0, space));
                String reasonPhrase = status.substring(space + 1);
                HttpResponseStatus responseStatus = HttpResponseStatus.valueOf(code);
                if (responseStatus.reasonPhrase().equals(reasonPhrase)) {
                    return responseStatus;
                } else {
                    return new HttpResponseStatus(code, reasonPhrase);
                }
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Sets the HTTP response getStatus header.
     */
    public static void setStatus(int spdyVersion, SpdyHeaderBlock block, HttpResponseStatus status) {
        if (spdyVersion < 3) {
            block.setHeader(Spdy2HttpNames.STATUS, status.toString());
        } else {
            block.setHeader(HttpNames.STATUS, status.toString());
        }
    }

    /**
     * Removes the URL path header.
     */
    public static void removeUrl(int spdyVersion, SpdyHeaderBlock block) {
        if (spdyVersion < 3) {
            block.removeHeader(Spdy2HttpNames.URL);
        } else {
            block.removeHeader(HttpNames.PATH);
        }
    }

    /**
     * Returns the value of the URL path header.
     */
    public static String getUrl(int spdyVersion, SpdyHeaderBlock block) {
        if (spdyVersion < 3) {
            return block.getHeader(Spdy2HttpNames.URL);
        } else {
            return block.getHeader(HttpNames.PATH);
        }
    }

    /**
     * Sets the URL path header.
     */
    public static void setUrl(int spdyVersion, SpdyHeaderBlock block, String path) {
        if (spdyVersion < 3) {
            block.setHeader(Spdy2HttpNames.URL, path);
        } else {
            block.setHeader(HttpNames.PATH, path);
        }
    }

    /**
     * Removes the HTTP version header.
     */
    public static void removeVersion(int spdyVersion, SpdyHeaderBlock block) {
        if (spdyVersion < 3) {
            block.removeHeader(Spdy2HttpNames.VERSION);
        } else {
            block.removeHeader(HttpNames.VERSION);
        }
    }

    /**
     * Returns the {@link HttpVersion} represented by the HTTP version header.
     */
    public static HttpVersion getVersion(int spdyVersion, SpdyHeaderBlock block) {
        try {
            if (spdyVersion < 3) {
                return HttpVersion.valueOf(block.getHeader(Spdy2HttpNames.VERSION));
            } else {
                return HttpVersion.valueOf(block.getHeader(HttpNames.VERSION));
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Sets the HTTP version header.
     */
    public static void setVersion(int spdyVersion, SpdyHeaderBlock block, HttpVersion httpVersion) {
        if (spdyVersion < 3) {
            block.setHeader(Spdy2HttpNames.VERSION, httpVersion.text());
        } else {
            block.setHeader(HttpNames.VERSION, httpVersion.text());
        }
    }

    private static final int BUCKET_SIZE = 17;

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

    SpdyHeaders() {
        head.before = head.after = head;
    }

    void addHeader(final String name, final Object value) {
        String lowerCaseName = name.toLowerCase();
        SpdyCodecUtil.validateHeaderName(lowerCaseName);
        String strVal = toString(value);
        SpdyCodecUtil.validateHeaderValue(strVal);
        int h = hash(lowerCaseName);
        int i = index(h);
        addHeader0(h, i, lowerCaseName, strVal);
    }

    private void addHeader0(int h, int i, final String name, final String value) {
        // Update the hash table.
        HeaderEntry e = entries[i];
        HeaderEntry newEntry;
        entries[i] = newEntry = new HeaderEntry(h, name, value);
        newEntry.next = e;

        // Update the linked list.
        newEntry.addBefore(head);
    }

    void removeHeader(final String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        String lowerCaseName = name.toLowerCase();
        int h = hash(lowerCaseName);
        int i = index(h);
        removeHeader0(h, i, lowerCaseName);
    }

    private void removeHeader0(int h, int i, String name) {
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

    void setHeader(final String name, final Object value) {
        String lowerCaseName = name.toLowerCase();
        SpdyCodecUtil.validateHeaderName(lowerCaseName);
        String strVal = toString(value);
        SpdyCodecUtil.validateHeaderValue(strVal);
        int h = hash(lowerCaseName);
        int i = index(h);
        removeHeader0(h, i, lowerCaseName);
        addHeader0(h, i, lowerCaseName, strVal);
    }

    void setHeader(final String name, final Iterable<?> values) {
        if (values == null) {
            throw new NullPointerException("values");
        }

        String lowerCaseName = name.toLowerCase();
        SpdyCodecUtil.validateHeaderName(lowerCaseName);

        int h = hash(lowerCaseName);
        int i = index(h);

        removeHeader0(h, i, lowerCaseName);
        for (Object v: values) {
            if (v == null) {
                break;
            }
            String strVal = toString(v);
            SpdyCodecUtil.validateHeaderValue(strVal);
            addHeader0(h, i, lowerCaseName, strVal);
        }
    }

    void clearHeaders() {
        for (int i = 0; i < entries.length; i ++) {
            entries[i] = null;
        }
        head.before = head.after = head;
    }

    String getHeader(final String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }

        int h = hash(name);
        int i = index(h);
        HeaderEntry e = entries[i];
        while (e != null) {
            if (e.hash == h && eq(name, e.key)) {
                return e.value;
            }

            e = e.next;
        }
        return null;
    }

    List<String> getHeaders(final String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }

        LinkedList<String> values = new LinkedList<String>();

        int h = hash(name);
        int i = index(h);
        HeaderEntry e = entries[i];
        while (e != null) {
            if (e.hash == h && eq(name, e.key)) {
                values.addFirst(e.value);
            }
            e = e.next;
        }
        return values;
    }

    List<Map.Entry<String, String>> getHeaders() {
        List<Map.Entry<String, String>> all =
            new LinkedList<Map.Entry<String, String>>();

        HeaderEntry e = head.after;
        while (e != head) {
            all.add(e);
            e = e.after;
        }
        return all;
    }

    boolean containsHeader(String name) {
        return getHeader(name) != null;
    }

    Set<String> getHeaderNames() {
        Set<String> names = new TreeSet<String>();

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
            SpdyCodecUtil.validateHeaderValue(value);
            String oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        @Override
        public String toString() {
            return key + '=' + value;
        }
    }
}
