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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultFullHttpResponseTest {

    @Test
    public void testEqualsForReleasedContentDoesNotThrow() {
        ByteBuf content1 = Unpooled.wrappedBuffer(new byte[] { 1, 2, 3 });
        ByteBuf content2 = Unpooled.wrappedBuffer(new byte[] { 1, 2, 3 });
        FullHttpResponse a = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content1);
        FullHttpResponse b = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content2);
        a.release();
        b.release();
        assertFalse(ByteBufUtil.isAccessible(content1));
        assertFalse(ByteBufUtil.isAccessible(content2));
        // hashCode tolerates released content; equals must do the same to honour the contract.
        assertEquals(a.hashCode(), b.hashCode());
        assertTrue(a.equals(b));
    }

    @Test
    public void testEqualsWhenOneSideReleasedReturnsFalse() {
        ByteBuf content1 = Unpooled.wrappedBuffer(new byte[] { 1, 2, 3 });
        ByteBuf content2 = Unpooled.wrappedBuffer(new byte[] { 1, 2, 3 });
        FullHttpResponse a = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content1);
        FullHttpResponse b = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content2);
        a.release();
        // Asymmetric accessibility: equals must not throw and must report not-equal.
        assertFalse(a.equals(b));
        assertFalse(b.equals(a));
        b.release();
    }

    @Test
    public void testEqualsForEqualLiveContent() {
        ByteBuf content1 = Unpooled.wrappedBuffer(new byte[] { 1, 2, 3 });
        ByteBuf content2 = Unpooled.wrappedBuffer(new byte[] { 1, 2, 3 });
        FullHttpResponse a = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content1);
        FullHttpResponse b = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content2);
        try {
            assertTrue(a.equals(b));
            assertEquals(a.hashCode(), b.hashCode());
        } finally {
            a.release();
            b.release();
        }
    }

    @Test
    public void testNotEqualsForDifferentLiveContent() {
        ByteBuf content1 = Unpooled.wrappedBuffer(new byte[] { 1, 2, 3 });
        ByteBuf content2 = Unpooled.wrappedBuffer(new byte[] { 4, 5, 6 });
        FullHttpResponse a = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content1);
        FullHttpResponse b = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content2);
        try {
            assertFalse(a.equals(b));
            assertNotEquals(a.hashCode(), b.hashCode());
        } finally {
            a.release();
            b.release();
        }
    }
}
