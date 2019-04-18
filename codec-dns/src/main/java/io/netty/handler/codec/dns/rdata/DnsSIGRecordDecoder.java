/*
 * Copyright 2019 The Netty Project
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

package io.netty.handler.codec.dns.rdata;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.dns.record.DnsSIGRecord;

import static io.netty.handler.codec.dns.util.DnsDecodeUtil.*;

/**
 * Decoder for {@link DnsSIGRecord}.
 */
public class DnsSIGRecordDecoder implements DnsRDataRecordDecoder<DnsSIGRecord> {
    public static final DnsSIGRecordDecoder DEFAULT = new DnsSIGRecordDecoder();

    @Override
    public DnsSIGRecord decodeRecordWithHeader(String name, int dnsClass, long timeToLive, ByteBuf rData) {
        checkShortReadable(rData, "typeCovered");
        short typeCovered = rData.readShort();
        checkByteReadable(rData, "algorithem");
        byte algorithem = rData.readByte();
        checkByteReadable(rData, "labels");
        byte labels = rData.readByte();
        checkIntReadable(rData, "originalTTL");
        int originalTTL = rData.readInt();
        checkIntReadable(rData, "expiration");
        int expiration = rData.readInt();
        checkIntReadable(rData, "inception");
        int inception = rData.readInt();
        checkShortReadable(rData, "keyTag");
        short keyTag = rData.readShort();
        String signerName = decodeDomainName(rData);
        String signature = decodeStringBase64(rData);
        return new DnsSIGRecord(name, dnsClass, timeToLive, typeCovered, algorithem, labels, originalTTL, expiration,
                                inception, keyTag, signerName, signature);
    }
}
