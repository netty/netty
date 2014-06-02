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
 * Decodes MX (mail exchanger) resource records.
 */
public class MailExchangerDecoder implements DnsResourceDecoder<MailExchangerRecord> {

    /**
     * Returns a decoded MX (mail exchanger) resource record, stored as an
     * instance of {@link MailExchangerRecord}.
     *
     * @param resource
     *            the {@link DnsResource} being decoded
     */
    @Override
    public MailExchangerRecord decode(DnsResource resource) {
        ByteBuf data = resource.content();
        int priority = data.readShort();
        String name = DnsResponseDecoder.readName(data);
        return new MailExchangerRecord(priority, name);
    }
}
