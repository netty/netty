/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.socks;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

final class SocksCommonTestUtils {
    /**
     * A constructor to stop this class being constructed.
     */
    private SocksCommonTestUtils() {
        //NOOP
    }

    @SuppressWarnings("deprecation")
    public static void writeMessageIntoEmbedder(EmbeddedChannel embedder, SocksMessage msg) {
        ByteBuf buf = Unpooled.buffer();
        msg.encodeAsByteBuf(buf);
        embedder.writeInbound(buf);
    }

    /**
     * Returns a domain name of exactly {@code length} characters made of labels of at most 63 characters.
     */
    static String domainOfLength(int length) {
        StringBuilder sb = new StringBuilder(length);
        while (sb.length() < length) {
            int remaining = length - sb.length();
            if (sb.length() > 0) {
                sb.append('.');
                remaining--;
            }
            int label = Math.min(63, remaining);
            if (remaining - label == 1) {
                // Never leave room for only a trailing dot.
                label--;
            }
            for (int i = 0; i < label; i++) {
                sb.append('a');
            }
        }
        return sb.toString();
    }

    static String asciiStringOfLength(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ('a' + i % 26));
        }
        return sb.toString();
    }
}
