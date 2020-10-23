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
import io.netty.buffer.ByteBufHolder;
import io.netty.util.internal.UnstableApi;

/**
 * A generic {@link DnsRecord} that contains an undecoded {@code RDATA}.
 */
@UnstableApi
public interface DnsRawRecord extends DnsRecord, ByteBufHolder {
    @Override
    DnsRawRecord copy();

    @Override
    DnsRawRecord duplicate();

    @Override
    DnsRawRecord retainedDuplicate();

    @Override
    DnsRawRecord replace(ByteBuf content);

    @Override
    DnsRawRecord retain();

    @Override
    DnsRawRecord retain(int increment);

    @Override
    DnsRawRecord touch();

    @Override
    DnsRawRecord touch(Object hint);
}
