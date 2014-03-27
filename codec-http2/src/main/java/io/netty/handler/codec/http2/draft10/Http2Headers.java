/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2.draft10;

import java.util.Iterator;
import java.util.Map.Entry;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMultimap;

public final class Http2Headers implements Iterable<Entry<String, String>> {

    /**
     * HTTP2 header names.
     */
    public enum HttpName {
        /**
         * {@code :method}.
         */
        METHOD(":method"),

        /**
         * {@code :scheme}.
         */
        SCHEME(":scheme"),

        /**
         * {@code :authority}.
         */
        AUTHORITY(":authority"),

        /**
         * {@code :path}.
         */
        PATH(":path"),

        /**
         * {@code :status}.
         */
        STATUS(":status");

        private final String value;

        private HttpName(String value) {
            this.value = value;
        }

        public String value() {
            return this.value;
        }
    }

    private final ImmutableMultimap<String, String> headers;

    private Http2Headers(Builder builder) {
        this.headers = builder.map.build();
    }

    public String getHeader(String name) {
        ImmutableCollection<String> col = getHeaders(name);
        return col.isEmpty() ? null : col.iterator().next();
    }

    public ImmutableCollection<String> getHeaders(String name) {
        return headers.get(name);
    }

    public String getMethod() {
        return getHeader(HttpName.METHOD.value());
    }

    public String getScheme() {
        return getHeader(HttpName.SCHEME.value());
    }

    public String getAuthority() {
        return getHeader(HttpName.AUTHORITY.value());
    }

    public String getPath() {
        return getHeader(HttpName.PATH.value());
    }

    public String getStatus() {
        return getHeader(HttpName.STATUS.value());
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {
        return headers.entries().iterator();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((headers == null) ? 0 : headers.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Http2Headers other = (Http2Headers) obj;
        if (headers == null) {
            if (other.headers != null) {
                return false;
            }
        } else if (!headers.equals(other.headers)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return headers.toString();
    }

    public static class Builder {
        private ImmutableMultimap.Builder<String, String> map = ImmutableMultimap.builder();

        public Builder clear() {
            map = ImmutableMultimap.builder();
            return this;
        }

        public Builder addHeaders(Http2Headers headers) {
            if (headers == null) {
                throw new IllegalArgumentException("headers must not be null.");
            }
            map.putAll(headers.headers);
            return this;
        }

        public Builder addHeader(String name, String value) {
            // Use interning on the header name to save space.
            map.put(name.intern(), value);
            return this;
        }

        public Builder addHeader(byte[] name, byte[] value) {
            addHeader(new String(name, Charsets.UTF_8), new String(value, Charsets.UTF_8));
            return this;
        }

        public Builder setMethod(String value) {
            return addHeader(HttpName.METHOD.value(), value);
        }

        public Builder setScheme(String value) {
            return addHeader(HttpName.SCHEME.value(), value);
        }

        public Builder setAuthority(String value) {
            return addHeader(HttpName.AUTHORITY.value(), value);
        }

        public Builder setPath(String value) {
            return addHeader(HttpName.PATH.value(), value);
        }

        public Builder setStatus(String value) {
            return addHeader(HttpName.STATUS.value(), value);
        }

        public Http2Headers build() {
            return new Http2Headers(this);
        }
    }
}
