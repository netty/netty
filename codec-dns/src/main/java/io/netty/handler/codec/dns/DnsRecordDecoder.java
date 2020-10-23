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
import io.netty.util.internal.UnstableApi;

/**
 * Decodes a DNS record into its object representation.
 *
 * @see DatagramDnsResponseDecoder
 */
@UnstableApi
public interface DnsRecordDecoder {

    DnsRecordDecoder DEFAULT = new DefaultDnsRecordDecoder();

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
