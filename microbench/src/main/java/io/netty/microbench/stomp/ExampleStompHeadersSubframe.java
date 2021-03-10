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
package io.netty.microbench.stomp;

import io.netty.handler.codec.stomp.DefaultStompHeadersSubframe;
import io.netty.handler.codec.stomp.StompCommand;
import io.netty.handler.codec.stomp.StompHeaders;
import io.netty.handler.codec.stomp.StompHeadersSubframe;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public final class ExampleStompHeadersSubframe {

    public enum HeadersType {
        ONE,
        THREE,
        SEVEN,
        // Next encoded headers size will be more than 256 bytes
        ELEVEN,
        TWENTY
    }

    public static final Map<HeadersType, StompHeadersSubframe> EXAMPLES =
            new EnumMap<HeadersType, StompHeadersSubframe>(
                    HeadersType.class);

    static {
        StompHeadersSubframe headersSubframe = new DefaultStompHeadersSubframe(StompCommand.RECEIPT);
        headersSubframe.headers()
                       .set(StompHeaders.RECEIPT_ID, UUID.randomUUID().toString());
        EXAMPLES.put(HeadersType.ONE, headersSubframe);

        headersSubframe = new DefaultStompHeadersSubframe(StompCommand.ERROR);
        headersSubframe.headers()
                       .set(StompHeaders.RECEIPT_ID, UUID.randomUUID().toString())
                       .set(StompHeaders.CONTENT_TYPE, "text/plain")
                       .set(StompHeaders.MESSAGE, "malformed frame received");
        EXAMPLES.put(HeadersType.THREE, headersSubframe);

        headersSubframe = new DefaultStompHeadersSubframe(StompCommand.MESSAGE);
        headersSubframe.headers()
                       .set(StompHeaders.SUBSCRIPTION, "7")
                       .set(StompHeaders.MESSAGE_ID, UUID.randomUUID().toString())
                       .set(StompHeaders.DESTINATION, "/queue/chat")
                       .set(StompHeaders.CONTENT_TYPE, "application/octet-stream")
                       .set(StompHeaders.ACK, UUID.randomUUID().toString())
                       .setLong("timestamp", System.currentTimeMillis())
                       .set("Message-Type: 007");
        EXAMPLES.put(HeadersType.SEVEN, headersSubframe);

        headersSubframe = new DefaultStompHeadersSubframe(StompCommand.MESSAGE);
        headersSubframe.headers()
                       .set(StompHeaders.SUBSCRIPTION, "11")
                       .set(StompHeaders.MESSAGE_ID, UUID.randomUUID().toString())
                       .set(StompHeaders.DESTINATION, "/queue/chat")
                       .set(StompHeaders.CONTENT_TYPE, "application/octet-stream")
                       .set(StompHeaders.ACK, UUID.randomUUID().toString())
                       .setLong("timestamp", System.currentTimeMillis())
                       .set("Message-Type: 0011")
                       .set("Strict-Transport-Security", "max-age=31536000; includeSubdomains; preload")
                       .set("Server", "GitHub.com")
                       .set("Expires", "Sat, 01 Jan 2000 00:00:00 GMT")
                       .set("Content-Language", "en");
        EXAMPLES.put(HeadersType.ELEVEN, headersSubframe);

        headersSubframe = new DefaultStompHeadersSubframe(StompCommand.MESSAGE);
        headersSubframe.headers()
                       .set(StompHeaders.SUBSCRIPTION, "20")
                       .set(StompHeaders.MESSAGE_ID, UUID.randomUUID().toString())
                       .set(StompHeaders.DESTINATION, "/queue/chat")
                       .set(StompHeaders.CONTENT_TYPE, "application/octet-stream")
                       .set(StompHeaders.ACK, UUID.randomUUID().toString())
                       .setLong("timestamp", System.currentTimeMillis())
                       .set("Message-Type: 0020")
                       .set("date", "Wed, 22 Apr 2015 00:40:28 GMT")
                       .set("expires", "Tue, 31 Mar 1981 05:00:00 GMT")
                       .set("last-modified", "Wed, 22 Apr 2015 00:40:28 GMT")
                       .set("ms", "ms")
                       .set("pragma", "no-cache")
                       .set("server", "tsa_b")
                       .set("set-cookie", "noneofyourbusiness")
                       .set("strict-transport-security", "max-age=631138519")
                       .set("version", "STOMP_v1.2")
                       .set("x-connection-hash", "e176fe40accc1e2c613a34bc1941aa98")
                       .set("x-content-type-options", "nosniff")
                       .set("x-frame-options", "SAMEORIGIN")
                       .set("x-transaction", "a54142ede693444d9");
        EXAMPLES.put(HeadersType.TWENTY, headersSubframe);
    }

    private ExampleStompHeadersSubframe() {
    }
}
