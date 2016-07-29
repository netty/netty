/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.codec.dns;

import io.netty.util.internal.UnstableApi;

/**
 * An <a href="https://tools.ietf.org/html/rfc6891#section-6.1">OPT RR</a> record.
 * <p>
 * This is used for <a href="https://tools.ietf.org/html/rfc6891#section-6.1.3">Extension
 * Mechanisms for DNS (EDNS(0))</a>.
 */
@UnstableApi
public interface DnsOptPseudoRecord extends DnsRecord {

    /**
     * Returns the {@code EXTENDED-RCODE} which is encoded into {@link DnsOptPseudoRecord#timeToLive()}.
     */
    int extendedRcode();

    /**
     * Returns the {@code VERSION} which is encoded into {@link DnsOptPseudoRecord#timeToLive()}.
     */
    int version();

    /**
     * Returns the {@code flags} which includes {@code DO} and {@code Z} which is encoded
     * into {@link DnsOptPseudoRecord#timeToLive()}.
     */
    int flags();
}
