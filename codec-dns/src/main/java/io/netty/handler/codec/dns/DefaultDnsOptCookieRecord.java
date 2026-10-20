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
package io.netty.handler.codec.dns;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Default {@link DnsOptCookieRecord} implementation.
 */
public final class DefaultDnsOptCookieRecord extends AbstractDnsOptPseudoRrRecord implements DnsOptCookieRecord {
    private final byte[] clientCookie;

    /**
     * Creates a new instance.
     *
     * @param maxPayloadSize    the suggested max payload size in bytes
     * @param clientCookie      the client cookie, must be exactly {@link #CLIENT_COOKIE_LENGTH} bytes
     */
    public DefaultDnsOptCookieRecord(int maxPayloadSize, byte[] clientCookie) {
        super(maxPayloadSize, 0, 0);
        this.clientCookie = verifyCookie(clientCookie).clone();
    }

    private static byte[] verifyCookie(byte[] clientCookie) {
        if (checkNotNull(clientCookie, "clientCookie").length != CLIENT_COOKIE_LENGTH) {
            throw new IllegalArgumentException(
                    "clientCookie.length must be " + CLIENT_COOKIE_LENGTH + " but was " + clientCookie.length);
        }
        return clientCookie;
    }

    @Override
    public byte[] clientCookie() {
        return clientCookie.clone();
    }
}
