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
package io.netty.handler.codec.spdy;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides the constants for the standard SPDY HTTP header names and commonly
 * used utility methods that access a {@link SpdyHeadersFrame}.
 */
public abstract class SpdyHeaders implements Iterable<Map.Entry<String, String>> {

    public static final SpdyHeaders EMPTY_HEADERS = new SpdyHeaders() {

        @Override
        public List<String> getAll(String name) {
            return Collections.emptyList();
        }

        @Override
        public List<Map.Entry<String, String>> entries() {
            return Collections.emptyList();
        }

        @Override
        public boolean contains(String name) {
            return false;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public Set<String> names() {
            return Collections.emptySet();
        }

        @Override
        public SpdyHeaders add(String name, Object value) {
            throw new UnsupportedOperationException("read only");
        }

        @Override
        public SpdyHeaders add(String name, Iterable<?> values) {
            throw new UnsupportedOperationException("read only");
        }

        @Override
        public SpdyHeaders set(String name, Object value) {
            throw new UnsupportedOperationException("read only");
        }

        @Override
        public SpdyHeaders set(String name, Iterable<?> values) {
            throw new UnsupportedOperationException("read only");
        }

        @Override
        public SpdyHeaders remove(String name) {
            throw new UnsupportedOperationException("read only");
        }

        @Override
        public SpdyHeaders clear() {
            throw new UnsupportedOperationException("read only");
        }

        @Override
        public Iterator<Map.Entry<String, String>> iterator() {
            return entries().iterator();
        }

        @Override
        public String get(String name) {
            return null;
        }
    };

    /**
     * SPDY HTTP header names
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

        private HttpNames() { }
    }

    /**
     * Returns the header value with the specified header name.  If there are
     * more than one header value for the specified header name, the first
     * value is returned.
     *
     * @return the header value or {@code null} if there is no such header
     */
    public static String getHeader(SpdyHeadersFrame frame, String name) {
        return frame.headers().get(name);
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
        String value = frame.headers().get(name);
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
        frame.headers().set(name, value);
    }

    /**
     * Sets a new header with the specified name and values.  If there is an
     * existing header with the same name, the existing header is removed.
     */
    public static void setHeader(SpdyHeadersFrame frame, String name, Iterable<?> values) {
        frame.headers().set(name, values);
    }

    /**
     * Adds a new header with the specified name and value.
     */
    public static void addHeader(SpdyHeadersFrame frame, String name, Object value) {
        frame.headers().add(name, value);
    }

    /**
     * Removes the SPDY host header.
     */
    public static void removeHost(SpdyHeadersFrame frame) {
        frame.headers().remove(HttpNames.HOST);
    }

    /**
     * Returns the SPDY host header.
     */
    public static String getHost(SpdyHeadersFrame frame) {
        return frame.headers().get(HttpNames.HOST);
    }

    /**
     * Set the SPDY host header.
     */
    public static void setHost(SpdyHeadersFrame frame, String host) {
        frame.headers().set(HttpNames.HOST, host);
    }

    /**
     * Removes the HTTP method header.
     */
    public static void removeMethod(int spdyVersion, SpdyHeadersFrame frame) {
        frame.headers().remove(HttpNames.METHOD);
    }

    /**
     * Returns the {@link HttpMethod} represented by the HTTP method header.
     */
    public static HttpMethod getMethod(int spdyVersion, SpdyHeadersFrame frame) {
        try {
            return HttpMethod.valueOf(frame.headers().get(HttpNames.METHOD));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Sets the HTTP method header.
     */
    public static void setMethod(int spdyVersion, SpdyHeadersFrame frame, HttpMethod method) {
        frame.headers().set(HttpNames.METHOD, method.name());
    }

    /**
     * Removes the URL scheme header.
     */
    public static void removeScheme(int spdyVersion, SpdyHeadersFrame frame) {
        frame.headers().remove(HttpNames.SCHEME);
    }

    /**
     * Returns the value of the URL scheme header.
     */
    public static String getScheme(int spdyVersion, SpdyHeadersFrame frame) {
        return frame.headers().get(HttpNames.SCHEME);
    }

    /**
     * Sets the URL scheme header.
     */
    public static void setScheme(int spdyVersion, SpdyHeadersFrame frame, String scheme) {
        frame.headers().set(HttpNames.SCHEME, scheme);
    }

    /**
     * Removes the HTTP response status header.
     */
    public static void removeStatus(int spdyVersion, SpdyHeadersFrame frame) {
        frame.headers().remove(HttpNames.STATUS);
    }

    /**
     * Returns the {@link HttpResponseStatus} represented by the HTTP response status header.
     */
    public static HttpResponseStatus getStatus(int spdyVersion, SpdyHeadersFrame frame) {
        try {
            String status = frame.headers().get(HttpNames.STATUS);
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
     * Sets the HTTP response status header.
     */
    public static void setStatus(int spdyVersion, SpdyHeadersFrame frame, HttpResponseStatus status) {
        frame.headers().set(HttpNames.STATUS, status.toString());
    }

    /**
     * Removes the URL path header.
     */
    public static void removeUrl(int spdyVersion, SpdyHeadersFrame frame) {
        frame.headers().remove(HttpNames.PATH);
    }

    /**
     * Returns the value of the URL path header.
     */
    public static String getUrl(int spdyVersion, SpdyHeadersFrame frame) {
        return frame.headers().get(HttpNames.PATH);
    }

    /**
     * Sets the URL path header.
     */
    public static void setUrl(int spdyVersion, SpdyHeadersFrame frame, String path) {
        frame.headers().set(HttpNames.PATH, path);
    }

    /**
     * Removes the HTTP version header.
     */
    public static void removeVersion(int spdyVersion, SpdyHeadersFrame frame) {
        frame.headers().remove(HttpNames.VERSION);
    }

    /**
     * Returns the {@link HttpVersion} represented by the HTTP version header.
     */
    public static HttpVersion getVersion(int spdyVersion, SpdyHeadersFrame frame) {
        try {
            return HttpVersion.valueOf(frame.headers().get(HttpNames.VERSION));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Sets the HTTP version header.
     */
    public static void setVersion(int spdyVersion, SpdyHeadersFrame frame, HttpVersion httpVersion) {
        frame.headers().set(HttpNames.VERSION, httpVersion.text());
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return entries().iterator();
    }

    /**
     * Returns the header value with the specified header name.  If there is
     * more than one header value for the specified header name, the first
     * value is returned.
     *
     * @return the header value or {@code null} if there is no such header
     */
    public abstract String get(String name);

    /**
     * Returns the header values with the specified header name.
     *
     * @return the {@link List} of header values.  An empty list if there is no
     *         such header.
     */
    public abstract List<String> getAll(String name);

    /**
     * Returns all header names and values that this frame contains.
     *
     * @return the {@link List} of the header name-value pairs.  An empty list
     *         if there is no header in this message.
     */
    public abstract List<Map.Entry<String, String>> entries();

    /**
     * Returns {@code true} if and only if there is a header with the specified
     * header name.
     */
    public abstract boolean contains(String name);

    /**
     * Returns the {@link Set} of all header names that this frame contains.
     */
    public abstract Set<String> names();

    /**
     * Adds a new header with the specified name and value.
     */
    public abstract SpdyHeaders add(String name, Object value);

    /**
     * Adds a new header with the specified name and values.  If there is an
     * existing header with the same name, the existing header is removed.
     */
    public abstract SpdyHeaders add(String name, Iterable<?> values);

    /**
     * Sets a new header with the specified name and value.  If there is an
     * existing header with the same name, the existing header is removed.
     */
    public abstract SpdyHeaders set(String name, Object value);

    /**
     * Sets a new header with the specified name and values.  If there is an
     * existing header with the same name, the existing header is removed.
     */
    public abstract SpdyHeaders set(String name, Iterable<?> values);

    /**
     * Removes the header with the specified name.
     */
    public abstract SpdyHeaders remove(String name);

    /**
     * Removes all headers from this frame.
     */
    public abstract SpdyHeaders clear();

    /**
     * Checks if no header exists.
     */
    public abstract boolean isEmpty();
}
