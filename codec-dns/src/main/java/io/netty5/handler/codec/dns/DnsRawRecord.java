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
package io.netty5.handler.codec.dns;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.Resource;
import io.netty5.util.internal.UnstableApi;

/**
 * A generic {@link DnsRecord} that contains an undecoded {@code RDATA}.
 */
@UnstableApi
public interface DnsRawRecord extends DnsRecord, Resource<DnsRawRecord> {
    /**
     * Get a reference to the {@link Buffer} backing this raw DNS record.
     * Care must be taken with the returned buffer, as changes to the buffer offsets or its contents will reflect on
     * the raw DNS record contents.
     *
     * @return The {@link Buffer} instance backing this raw DNS record.
     */
    Buffer content();

    @Override
    DnsRawRecord touch(Object hint);
}
