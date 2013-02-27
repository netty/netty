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

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides the constants for the standard SPDY HTTP header names and commonly
 * used utility methods that access a {@link SpdyHeaderBlock}.
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
     * SPDY/2 HTTP header names
     */
    public static final class Spdy2HttpNames {
        /**
         * {@code "method"}
         */
        public static final String METHOD = "method";
        /**
         * {@code "scheme"}
         */
        public static final String SCHEME = "scheme";
        /**
         * {@code "status"}
         */
        public static final String STATUS = "status";
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
        return block.headers().get(name);
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
        String value = block.headers().get(name);
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
        block.headers().set(name, value);
    }

    /**
     * Sets a new header with the specified name and values.  If there is an
     * existing header with the same name, the existing header is removed.
     */
    public static void setHeader(SpdyHeaderBlock block, String name, Iterable<?> values) {
        block.headers().set(name, values);
    }

    /**
     * Adds a new header with the specified name and value.
     */
    public static void addHeader(SpdyHeaderBlock block, String name, Object value) {
        block.headers().add(name, value);
    }

    /**
     * Removes the SPDY host header.
     */
    public static void removeHost(SpdyHeaderBlock block) {
        block.headers().remove(HttpNames.HOST);
    }

    /**
     * Returns the SPDY host header.
     */
    public static String getHost(SpdyHeaderBlock block) {
        return block.headers().get(HttpNames.HOST);
    }

    /**
     * Set the SPDY host header.
     */
    public static void setHost(SpdyHeaderBlock block, String host) {
        block.headers().set(HttpNames.HOST, host);
    }

    /**
     * Removes the HTTP method header.
     */
    public static void removeMethod(int spdyVersion, SpdyHeaderBlock block) {
        if (spdyVersion < 3) {
            block.headers().remove(Spdy2HttpNames.METHOD);
        } else {
            block.headers().remove(HttpNames.METHOD);
        }
    }

    /**
     * Returns the {@link HttpMethod} represented by the HTTP method header.
     */
    public static HttpMethod getMethod(int spdyVersion, SpdyHeaderBlock block) {
        try {
            if (spdyVersion < 3) {
                return HttpMethod.valueOf(block.headers().get(Spdy2HttpNames.METHOD));
            } else {
                return HttpMethod.valueOf(block.headers().get(HttpNames.METHOD));
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Sets the HTTP method header.
     */
    public static void setMethod(int spdyVersion, SpdyHeaderBlock block, HttpMethod method) {
        if (spdyVersion < 3) {
            block.headers().set(Spdy2HttpNames.METHOD, method.name());
        } else {
            block.headers().set(HttpNames.METHOD, method.name());
        }
    }

    /**
     * Removes the URL scheme header.
     */
    public static void removeScheme(int spdyVersion, SpdyHeaderBlock block) {
        if (spdyVersion < 2) {
            block.headers().remove(Spdy2HttpNames.SCHEME);
        } else {
            block.headers().remove(HttpNames.SCHEME);
        }
    }

    /**
     * Returns the value of the URL scheme header.
     */
    public static String getScheme(int spdyVersion, SpdyHeaderBlock block) {
        if (spdyVersion < 3) {
            return block.headers().get(Spdy2HttpNames.SCHEME);
        } else {
            return block.headers().get(HttpNames.SCHEME);
        }
    }

    /**
     * Sets the URL scheme header.
     */
    public static void setScheme(int spdyVersion, SpdyHeaderBlock block, String scheme) {
        if (spdyVersion < 3) {
            block.headers().set(Spdy2HttpNames.SCHEME, scheme);
        } else {
            block.headers().set(HttpNames.SCHEME, scheme);
        }
    }

    /**
     * Removes the HTTP response status header.
     */
    public static void removeStatus(int spdyVersion, SpdyHeaderBlock block) {
        if (spdyVersion < 3) {
            block.headers().remove(Spdy2HttpNames.STATUS);
        } else {
            block.headers().remove(HttpNames.STATUS);
        }
    }

    /**
     * Returns the {@link HttpResponseStatus} represented by the HTTP response status header.
     */
    public static HttpResponseStatus getStatus(int spdyVersion, SpdyHeaderBlock block) {
        try {
            String status;
            if (spdyVersion < 3) {
                status = block.headers().get(Spdy2HttpNames.STATUS);
            } else {
                status = block.headers().get(HttpNames.STATUS);
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
     * Sets the HTTP response status header.
     */
    public static void setStatus(int spdyVersion, SpdyHeaderBlock block, HttpResponseStatus status) {
        if (spdyVersion < 3) {
            block.headers().set(Spdy2HttpNames.STATUS, status.toString());
        } else {
            block.headers().set(HttpNames.STATUS, status.toString());
        }
    }

    /**
     * Removes the URL path header.
     */
    public static void removeUrl(int spdyVersion, SpdyHeaderBlock block) {
        if (spdyVersion < 3) {
            block.headers().remove(Spdy2HttpNames.URL);
        } else {
            block.headers().remove(HttpNames.PATH);
        }
    }

    /**
     * Returns the value of the URL path header.
     */
    public static String getUrl(int spdyVersion, SpdyHeaderBlock block) {
        if (spdyVersion < 3) {
            return block.headers().get(Spdy2HttpNames.URL);
        } else {
            return block.headers().get(HttpNames.PATH);
        }
    }

    /**
     * Sets the URL path header.
     */
    public static void setUrl(int spdyVersion, SpdyHeaderBlock block, String path) {
        if (spdyVersion < 3) {
            block.headers().set(Spdy2HttpNames.URL, path);
        } else {
            block.headers().set(HttpNames.PATH, path);
        }
    }

    /**
     * Removes the HTTP version header.
     */
    public static void removeVersion(int spdyVersion, SpdyHeaderBlock block) {
        if (spdyVersion < 3) {
            block.headers().remove(Spdy2HttpNames.VERSION);
        } else {
            block.headers().remove(HttpNames.VERSION);
        }
    }

    /**
     * Returns the {@link HttpVersion} represented by the HTTP version header.
     */
    public static HttpVersion getVersion(int spdyVersion, SpdyHeaderBlock block) {
        try {
            if (spdyVersion < 3) {
                return HttpVersion.valueOf(block.headers().get(Spdy2HttpNames.VERSION));
            } else {
                return HttpVersion.valueOf(block.headers().get(HttpNames.VERSION));
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
            block.headers().set(Spdy2HttpNames.VERSION, httpVersion.text());
        } else {
            block.headers().set(HttpNames.VERSION, httpVersion.text());
        }
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
    public abstract  List<String> getAll(String name);

    /**
     * Returns all header names and values that this block contains.
     *
     * @return the {@link List} of the header name-value pairs.  An empty list
     *         if there is no header in this message.
     */
    public abstract  List<Map.Entry<String, String>> entries();

    /**
     * Returns {@code true} if and only if there is a header with the specified
     * header name.
     */
    public abstract  boolean contains(String name);

    /**
     * Returns the {@link Set} of all header names that this block contains.
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
     * Removes all headers from this block.
     */
    public abstract SpdyHeaders clear();

    /**
     * Checks if no header exists.
     */
    public abstract boolean isEmpty();
}
