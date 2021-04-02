/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

/*
 * Copyright 2015 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.netty.handler.codec.http2;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility methods for hpack tests.
 */
public final class HpackBenchmarkUtil {
    private HpackBenchmarkUtil() {
    }

    /**
     * Internal key used to index a particular set of headers in the map.
     */
    private static class HeadersKey {
        final HpackHeadersSize size;
        final boolean limitToAscii;

        HeadersKey(HpackHeadersSize size, boolean limitToAscii) {
            this.size = size;
            this.limitToAscii = limitToAscii;
        }

        List<HpackHeader> newHeaders() {
            return size.newHeaders(limitToAscii);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            HeadersKey that = (HeadersKey) o;

            if (limitToAscii != that.limitToAscii) {
                return false;
            }
            return size == that.size;
        }

        @Override
        public int hashCode() {
            int result = size.hashCode();
            result = 31 * result + (limitToAscii ? 1 : 0);
            return result;
        }
    }

    private static final Map<HeadersKey, List<HpackHeader>> headersMap;

    static {
        HpackHeadersSize[] sizes = HpackHeadersSize.values();
        headersMap = new HashMap<HeadersKey, List<HpackHeader>>(sizes.length * 2);
        for (HpackHeadersSize size : sizes) {
            HeadersKey key = new HeadersKey(size, true);
            headersMap.put(key, key.newHeaders());

            key = new HeadersKey(size, false);
            headersMap.put(key, key.newHeaders());
        }
    }

    /**
     * Gets headers for the given size and whether the key/values should be limited to ASCII.
     */
    static List<HpackHeader> headers(HpackHeadersSize size, boolean limitToAscii) {
        return headersMap.get(new HeadersKey(size, limitToAscii));
    }

    static Http2Headers http2Headers(HpackHeadersSize size, boolean limitToAscii) {
        List<HpackHeader> hpackHeaders = headersMap.get(new HeadersKey(size, limitToAscii));
        Http2Headers http2Headers = new DefaultHttp2Headers(false);
        for (int i = 0; i < hpackHeaders.size(); ++i) {
            HpackHeader hpackHeader = hpackHeaders.get(i);
            http2Headers.add(hpackHeader.name, hpackHeader.value);
        }
        return http2Headers;
    }
}
