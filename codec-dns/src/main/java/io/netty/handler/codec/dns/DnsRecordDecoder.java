/*
 * Copyright 2015 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.SystemPropertyUtil;

/**
 * Decodes a DNS record into its object representation.
 * <p>
 * The behavior of the {@link #DEFAULT} decoder can be controlled via the system property
 * {@code io.netty.dns.decoder.legacyMode}. When {@code true} (the default), the decoder uses
 * legacy behavior (Netty &lt;=4.2.8) returning {@link DefaultDnsRawRecord} for most types.
 * When {@code false}, structured record types are returned where implemented (e.g.,
 * {@link DnsARecord}, {@link DnsMxRecord}); unsupported types still return
 * {@link DefaultDnsRawRecord}.
 *
 * @see DatagramDnsResponseDecoder
 */
public interface DnsRecordDecoder {

    DnsRecordDecoder DEFAULT = new DefaultDnsRecordDecoder(
            SystemPropertyUtil.getBoolean("io.netty.dns.decoder.legacyMode", true));

    /**
     * Decodes a DNS question into its object representation.
     *
     * @param in the input buffer which contains a DNS question at its reader index
     */
    DnsQuestion decodeQuestion(ByteBuf in) throws Exception;

    /**
     * Decodes a DNS record into its object representation.
     *
     * @param in the input buffer which contains a DNS record at its reader index
     *
     * @return the decoded record, or {@code null} if there are not enough data in the input buffer
     */
    <T extends DnsRecord> T decodeRecord(ByteBuf in) throws Exception;
}
