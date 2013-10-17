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
package org.jboss.netty.handler.codec.spdy;

import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Provides the constants for the standard SPDY HTTP header names and commonly
 * used utility methods that access a {@link SpdyHeadersFrame}.
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
         * {@code ":method"}
         */
        public static final String METHOD = ":method";
        /**
         * {@code ":path"}
         */
        public static final String PATH = ":path";
        /**
         * {@code ":scheme"}
         */
        public static final String SCHEME = ":scheme";
        /**
         * {@code ":status"}
         */
        public static final String STATUS = ":status";
        /**
         * {@code ":version"}
         */
        public static final String VERSION = ":version";

        private HttpNames() {
        }
    }

    /**
     * Returns the header value with the specified header name.  If there are
     * more than one header value for the specified header name, the first
     * value is returned.
     *
     * @return the header value or {@code null} if there is no such header
     */
    public static String getHeader(SpdyHeadersFrame frame, String name) {
        return frame.getHeader(name);
    }

    /**
     * Returns the header value with the specified header name.  If there are
     * more than one header value for the specified header name, the first
     * value is returned.
     *
     * @return the header value or the {@code defaultValue} if there is no such
     *         header
     */
    public static String getHeader(SpdyHeadersFrame frame, String name, String defaultValue) {
        String value = frame.getHeader(name);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    /**
     * Sets a new header with the specified name and value.  If there is an
     * existing header with the same name, the existing header is removed.
     */
    public static void setHeader(SpdyHeadersFrame frame, String name, Object value) {
        frame.setHeader(name, value);
    }

    /**
     * Sets a new header with the specified name and values.  If there is an
     * existing header with the same name, the existing header is removed.
     */
    public static void setHeader(SpdyHeadersFrame frame, String name, Iterable<?> values) {
        frame.setHeader(name, values);
    }

    /**
     * Adds a new header with the specified name and value.
     */
    public static void addHeader(SpdyHeadersFrame frame, String name, Object value) {
        frame.addHeader(name, value);
    }

    /**
     * Removes the SPDY host header.
     */
    public static void removeHost(SpdyHeadersFrame frame) {
        frame.removeHeader(HttpNames.HOST);
    }

    /**
     * Returns the SPDY host header.
     */
    public static String getHost(SpdyHeadersFrame frame) {
        return frame.getHeader(HttpNames.HOST);
    }

    /**
     * Set the SPDY host header.
     */
    public static void setHost(SpdyHeadersFrame frame, String host) {
        frame.setHeader(HttpNames.HOST, host);
    }

    /**
     * Removes the HTTP method header.
     */
    public static void removeMethod(int spdyVersion, SpdyHeadersFrame frame) {
        frame.removeHeader(HttpNames.METHOD);
    }

    /**
     * Returns the {@link HttpMethod} represented by the HTTP method header.
     */
    public static HttpMethod getMethod(int spdyVersion, SpdyHeadersFrame frame) {
        try {
            return HttpMethod.valueOf(frame.getHeader(HttpNames.METHOD));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Sets the HTTP method header.
     */
    public static void setMethod(int spdyVersion, SpdyHeadersFrame frame, HttpMethod method) {
        frame.setHeader(HttpNames.METHOD, method.getName());
    }

    /**
     * Removes the URL scheme header.
     */
    public static void removeScheme(int spdyVersion, SpdyHeadersFrame frame) {
        frame.removeHeader(HttpNames.SCHEME);
    }

    /**
     * Returns the value of the URL scheme header.
     */
    public static String getScheme(int spdyVersion, SpdyHeadersFrame frame) {
        return frame.getHeader(HttpNames.SCHEME);
    }

    /**
     * Sets the URL scheme header.
     */
    public static void setScheme(int spdyVersion, SpdyHeadersFrame frame, String scheme) {
        frame.setHeader(HttpNames.SCHEME, scheme);
    }

    /**
     * Removes the HTTP response status header.
     */
    public static void removeStatus(int spdyVersion, SpdyHeadersFrame frame) {
        frame.removeHeader(HttpNames.STATUS);
    }

    /**
     * Returns the {@link HttpResponseStatus} represented by the HTTP response status header.
     */
    public static HttpResponseStatus getStatus(int spdyVersion, SpdyHeadersFrame frame) {
        try {
            String status = frame.getHeader(HttpNames.STATUS);
            int space = status.indexOf(' ');
            if (space == -1) {
                return HttpResponseStatus.valueOf(Integer.parseInt(status));
            } else {
                int code = Integer.parseInt(status.substring(0, space));
                String reasonPhrase = status.substring(space + 1);
                HttpResponseStatus responseStatus = HttpResponseStatus.valueOf(code);
                if (responseStatus.getReasonPhrase().equals(reasonPhrase)) {
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
     * Sets the HTTP response status header.
     */
    public static void setStatus(int spdyVersion, SpdyHeadersFrame frame, HttpResponseStatus status) {
        frame.setHeader(HttpNames.STATUS, status.toString());
    }

    /**
     * Removes the URL path header.
     */
    public static void removeUrl(int spdyVersion, SpdyHeadersFrame frame) {
        frame.removeHeader(HttpNames.PATH);
    }

    /**
     * Returns the value of the URL path header.
     */
    public static String getUrl(int spdyVersion, SpdyHeadersFrame frame) {
        return frame.getHeader(HttpNames.PATH);
    }

    /**
     * Sets the URL path header.
     */
    public static void setUrl(int spdyVersion, SpdyHeadersFrame frame, String path) {
        frame.setHeader(HttpNames.PATH, path);
    }

    /**
     * Removes the HTTP version header.
     */
    public static void removeVersion(int spdyVersion, SpdyHeadersFrame frame) {
        frame.removeHeader(HttpNames.VERSION);
    }

    /**
     * Returns the {@link HttpVersion} represented by the HTTP version header.
     */
    public static HttpVersion getVersion(int spdyVersion, SpdyHeadersFrame frame) {
        try {
            return HttpVersion.valueOf(frame.getHeader(HttpNames.VERSION));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Sets the HTTP version header.
     */
    public static void setVersion(int spdyVersion, SpdyHeadersFrame frame, HttpVersion httpVersion) {
        frame.setHeader(HttpNames.VERSION, httpVersion.getText());
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

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

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
