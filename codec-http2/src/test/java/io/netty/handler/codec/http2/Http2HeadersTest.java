/*
 * Copyright 2024 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName;
import io.netty.util.AsciiString;
import org.junit.jupiter.api.Test;

import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.getPseudoHeader;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.isPseudoHeader;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Http2HeadersTest {

    @Test
    public void testGetPseudoHeader() {
        for (PseudoHeaderName pseudoHeaderName : PseudoHeaderName.values()) {
            assertSame(pseudoHeaderName, getPseudoHeader(pseudoHeaderName.value()));
            assertSame(pseudoHeaderName, getPseudoHeader(new AsciiString(pseudoHeaderName.value().array())));
            assertSame(pseudoHeaderName, getPseudoHeader(pseudoHeaderName.value().toString()));
            assertSame(pseudoHeaderName, getPseudoHeader(new String(pseudoHeaderName.value().toCharArray())));
            assertSame(pseudoHeaderName, getPseudoHeader(new StringBuilder(pseudoHeaderName.value())));
        }
    }

    @Test
    public void pseudoHeaderNamesAreLiterals() {
        // despite is an implementation details. is a relevant optimization
        assertSame(":authority", PseudoHeaderName.AUTHORITY.value().toString());
        assertSame(":method", PseudoHeaderName.METHOD.value().toString());
        assertSame(":path", PseudoHeaderName.PATH.value().toString());
        assertSame(":scheme", PseudoHeaderName.SCHEME.value().toString());
        assertSame(":status", PseudoHeaderName.STATUS.value().toString());
        assertSame(":protocol", PseudoHeaderName.PROTOCOL.value().toString());
    }

    @Test
    public void testIsPseudoHeader() {
        // same as before but for isPseudoHeader
        for (PseudoHeaderName pseudoHeaderName : PseudoHeaderName.values()) {
            assertTrue(isPseudoHeader(pseudoHeaderName.value()));
            assertTrue(isPseudoHeader(new AsciiString(pseudoHeaderName.value().array())));
            assertTrue(isPseudoHeader(pseudoHeaderName.value().toString()));
            assertTrue(isPseudoHeader(new String(pseudoHeaderName.value().toCharArray())));
            assertTrue(isPseudoHeader(new StringBuilder(pseudoHeaderName.value())));
        }
    }
}
