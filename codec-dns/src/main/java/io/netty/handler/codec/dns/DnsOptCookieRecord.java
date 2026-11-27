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

/**
 * An {@link DnsOptPseudoRecord} that carries a client cookie as defined in
 * <a href="https://tools.ietf.org/html/rfc7873#section-4">RFC 7873, Domain Name System (DNS) Cookies</a>.
 */
public interface DnsOptCookieRecord extends DnsOptPseudoRecord {

    /**
     * The length of a client cookie in bytes.
     */
    int CLIENT_COOKIE_LENGTH = 8;

    /**
     * The {@code OPTION-CODE} that is assigned to {@code COOKIE}.
     */
    int OPTION_CODE = 10;

    /**
     * Returns the client cookie, which is always {@link #CLIENT_COOKIE_LENGTH} bytes long.
     * The returned array is a copy.
     */
    byte[] clientCookie();
}
