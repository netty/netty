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
package io.netty.dns.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResource;
import io.netty.util.CharsetUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Decodes TXT (text) resource records.
 */
public class TextDecoder implements RecordDecoder<List<String>> {

    /**
     * Returns a decoded TXT (text) resource record, stored as an
     * {@link ArrayList} of {@code String}s.
     *
     * @param response
     *            the DNS response that contains the resource record being
     *            decoded
     * @param resource
     *            the resource record being decoded
     */
    @Override
    public List<String> decode(DnsResponse response, DnsResource resource) {
        List<String> list = new ArrayList<String>();
        ByteBuf data = resource.content().readerIndex(response.originalIndex());
        int index = data.readerIndex();
        while (index < data.writerIndex()) {
            int len = data.getUnsignedByte(index++);
            list.add(data.toString(index, len, CharsetUtil.UTF_8));
            index += len;
        }
        return list;
    }

}
