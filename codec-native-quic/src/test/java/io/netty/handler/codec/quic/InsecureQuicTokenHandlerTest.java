/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class InsecureQuicTokenHandlerTest extends AbstractQuicTest {

    @Test
    public void testMaxTokenLength() {
        assertEquals(InsecureQuicTokenHandler.MAX_TOKEN_LEN, InsecureQuicTokenHandler.INSTANCE.maxTokenLength());
    }

    @Test
    public void testTokenProcessingIpv4() throws UnknownHostException {
        testTokenProcessing(true);
    }

    @Test
    public void testTokenProcessingIpv6() throws UnknownHostException {
        testTokenProcessing(false);
    }

    private static void testTokenProcessing(boolean ipv4) throws UnknownHostException {
        byte[] bytes = new byte[Quiche.QUICHE_MAX_CONN_ID_LEN];
        ThreadLocalRandom.current().nextBytes(bytes);
        ByteBuf dcid = Unpooled.wrappedBuffer(bytes);
        ByteBuf out = Unpooled.buffer();
        try {
            final InetSocketAddress validAddress;
            final InetSocketAddress invalidAddress;
            if (ipv4) {
                validAddress = new InetSocketAddress(
                        InetAddress.getByAddress(new byte[] { 10, 10, 10, 1}), 9999);
                invalidAddress  = new InetSocketAddress(
                        InetAddress.getByAddress(new byte[] { 10, 10, 10, 10}), 9999);
            } else {
                validAddress = new InetSocketAddress(InetAddress.getByAddress(
                        new byte[] { 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 1}), 9999);
                invalidAddress  = new InetSocketAddress(InetAddress.getByAddress(
                        new byte[] { 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10}), 9999);
            }

            InsecureQuicTokenHandler.INSTANCE.writeToken(out, dcid, validAddress);
            assertThat(out.readableBytes(), lessThanOrEqualTo(InsecureQuicTokenHandler.INSTANCE.maxTokenLength()));
            assertNotEquals(-1, InsecureQuicTokenHandler.INSTANCE.validateToken(out, validAddress));

            // Use another address and check that the validate fails.
            assertEquals(-1, InsecureQuicTokenHandler.INSTANCE.validateToken(out, invalidAddress));
        } finally {
            dcid.release();
            out.release();
        }
    }
}
