/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.dns.resolver;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.dns.DnsResource;
import io.netty.handler.codec.dns.DnsResponseDecoder;

/**
 * Decodes SOA (start of authority) resource records.
 */
public final class StartOfAuthorityDecoder implements DnsResourceDecoder<StartOfAuthorityRecord> {

    /**
     * Returns a decoded SOA (start of authority) resource record, stored as an
     * instance of {@link StartOfAuthorityRecord}.
     *
     * @param resource
     *            the resource record being decoded
     */
    @Override
    public StartOfAuthorityRecord decode(DnsResource resource) {
        ByteBuf data = resource.content();
        String mName = DnsResponseDecoder.readName(data);
        String rName = DnsResponseDecoder.readName(data);
        long serial = data.readUnsignedInt();
        int refresh = data.readInt();
        int retry = data.readInt();
        int expire = data.readInt();
        long minimum = data.readUnsignedInt();
        return new StartOfAuthorityRecord(mName, rName, serial, refresh, retry, expire, minimum);
    }

}
