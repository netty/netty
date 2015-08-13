/*
 * Copyright 2014 The Netty Project
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

package io.netty.handler.codec.http2;

import io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName;
import io.netty.util.ByteString;
import org.junit.Test;

import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import static io.netty.util.ByteString.fromAscii;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DefaultHttp2HeadersTest {

    @Test
    public void pseudoHeadersMustComeFirstWhenIterating() {
        Http2Headers headers = newHeaders();

        verifyPseudoHeadersFirst(headers);
        verifyAllPseudoHeadersPresent(headers);
    }

    @Test
    public void pseudoHeadersWithRemovePreservesPseudoIterationOrder() {
        Http2Headers headers = newHeaders();

        Set<ByteString> nonPseudoHeaders = new HashSet<ByteString>(headers.size());
        for (Entry<ByteString, ByteString> entry : headers) {
            if (entry.getKey().isEmpty() || entry.getKey().byteAt(0) != ':') {
                nonPseudoHeaders.add(entry.getKey());
            }
        }

        // Remove all the non-pseudo headers and verify
        for (ByteString nonPseudoHeader : nonPseudoHeaders) {
            assertTrue(headers.remove(nonPseudoHeader));
            verifyPseudoHeadersFirst(headers);
            verifyAllPseudoHeadersPresent(headers);
        }

        // Add back all non-pseudo headers
        for (ByteString nonPseudoHeader : nonPseudoHeaders) {
            headers.add(nonPseudoHeader, fromAscii("goo"));
            verifyPseudoHeadersFirst(headers);
            verifyAllPseudoHeadersPresent(headers);
        }
    }

    private static void verifyAllPseudoHeadersPresent(Http2Headers headers) {
        for (PseudoHeaderName pseudoName : PseudoHeaderName.values()) {
            assertNotNull(headers.get(pseudoName.value()));
        }
    }

    private static void verifyPseudoHeadersFirst(Http2Headers headers) {
        ByteString lastNonPseudoName = null;
        for (Entry<ByteString, ByteString> entry: headers) {
            if (entry.getKey().isEmpty() || entry.getKey().byteAt(0) != ':') {
                lastNonPseudoName = entry.getKey();
            } else if (lastNonPseudoName != null) {
                fail("All pseudo headers must be fist in iteration. Pseudo header " + entry.getKey() +
                        " is after a non pseudo header " + lastNonPseudoName);
            }
        }
    }

    private static Http2Headers newHeaders() {
        Http2Headers headers = new DefaultHttp2Headers();
        headers.add(fromAscii("name1"), fromAscii("value1"), fromAscii("value2"));
        headers.method(fromAscii("POST"));
        headers.add(fromAscii("2name"), fromAscii("value3"));
        headers.path(fromAscii("/index.html"));
        headers.status(fromAscii("200"));
        headers.authority(fromAscii("netty.io"));
        headers.add(fromAscii("name3"), fromAscii("value4"));
        headers.scheme(fromAscii("https"));
        return headers;
    }
}
