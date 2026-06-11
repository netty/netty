/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HttpMethodTest {

    private static final String NUL = String.valueOf((char) 0x00);

    @Test
    public void valueOfReturnsCachedInstanceForKnownMethods() {
        assertSame(HttpMethod.GET, HttpMethod.valueOf("GET"));
        assertSame(HttpMethod.POST, HttpMethod.valueOf("POST"));
    }

    @Test
    public void constructorAcceptsCustomMethodName() {
        HttpMethod custom = new HttpMethod("CUSTOM");
        assertEquals("CUSTOM", custom.name());
    }

    @Test
    public void constructorRejectsLeadingAndTrailingSpaces() {
        // SP is not a valid HTTP token character; the name must already be a clean token.
        assertThrows(IllegalArgumentException.class, newInstance("  GET  "));
    }

    @Test
    public void constructorRejectsLeadingAndTrailingTabs() {
        // HT is not a valid HTTP token character; the name must already be a clean token.
        assertThrows(IllegalArgumentException.class, newInstance("\tGET\t"));
    }

    @Test
    public void constructorRejectsEmptyName() {
        assertThrows(IllegalArgumentException.class, newInstance(""));
    }

    @Test
    public void constructorRejectsLeadingNul() {
        assertThrows(IllegalArgumentException.class, newInstance(NUL + "GET"));
    }

    @Test
    public void constructorRejectsTrailingNul() {
        assertThrows(IllegalArgumentException.class, newInstance("GET" + NUL));
    }

    @Test
    public void constructorRejectsLeadingAndTrailingNul() {
        assertThrows(IllegalArgumentException.class, newInstance(NUL + "GET" + NUL));
    }

    @Test
    public void constructorRejectsEmbeddedNul() {
        assertThrows(IllegalArgumentException.class, newInstance("GE" + NUL + "T"));
    }

    @Test
    public void constructorRejectsCarriageReturn() {
        assertThrows(IllegalArgumentException.class, newInstance("GET\r"));
    }

    @Test
    public void constructorRejectsLineFeed() {
        assertThrows(IllegalArgumentException.class, newInstance("GET\n"));
    }

    @Test
    public void constructorRejectsVerticalTab() {
        assertThrows(IllegalArgumentException.class, newInstance("GET" + (char) 0x0B));
    }

    @Test
    public void constructorRejectsFormFeed() {
        assertThrows(IllegalArgumentException.class, newInstance("GET\f"));
    }

    @Test
    public void constructorRejectsEmbeddedSpace() {
        assertThrows(IllegalArgumentException.class, newInstance("GE T"));
    }

    @Test
    public void constructorRejectsEmptyString() {
        assertThrows(IllegalArgumentException.class, newInstance(""));
    }

    @Test
    public void constructorRejectsBlankString() {
        assertThrows(IllegalArgumentException.class, newInstance("   "));
    }

    @Test
    public void requestDecoderRejectsNulPaddedMethod() {
        // RFC 9112 forbids any non-token character in the method. NUL-padded methods are
        // a known request-smuggling vector if silently stripped, so the decoder must
        // surface a decoder failure rather than producing a valid GET message.
        EmbeddedChannel ch = new EmbeddedChannel(new HttpRequestDecoder());
        try {
            byte[] data = (NUL + "GET" + NUL + " / HTTP/1.1\r\nHost: x\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII);
            ch.writeInbound(Unpooled.wrappedBuffer(data));
            HttpRequest req = ch.readInbound();
            try {
                assertNotNull(req);
                assertFalse(req.decoderResult().isSuccess(),
                        "decoder must reject method names containing NUL bytes");
            } finally {
                ReferenceCountUtil.release(req);
            }
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    @Test
    public void requestDecoderAcceptsCleanMethod() {
        // Regression: ordinary GET requests must still parse normally.
        EmbeddedChannel ch = new EmbeddedChannel(new HttpRequestDecoder());
        try {
            byte[] data = "GET / HTTP/1.1\r\nHost: x\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
            ch.writeInbound(Unpooled.wrappedBuffer(data));
            HttpRequest req = ch.readInbound();
            try {
                assertNotNull(req);
                assertEquals(HttpMethod.GET, req.method());
            } finally {
                ReferenceCountUtil.release(req);
            }
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    private static Executable newInstance(final String name) {
        return new Executable() {
            @Override
            public void execute() {
                new HttpMethod(name);
            }
        };
    }
}
