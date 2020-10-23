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

import io.netty.util.internal.UnstableApi;

/**
 * A DNS query message.
 */
@UnstableApi
public interface DnsQuery extends DnsMessage {
    @Override
    DnsQuery setId(int id);

    @Override
    DnsQuery setOpCode(DnsOpCode opCode);

    @Override
    DnsQuery setRecursionDesired(boolean recursionDesired);

    @Override
    DnsQuery setZ(int z);

    @Override
    DnsQuery setRecord(DnsSection section, DnsRecord record);

    @Override
    DnsQuery addRecord(DnsSection section, DnsRecord record);

    @Override
    DnsQuery addRecord(DnsSection section, int index, DnsRecord record);

    @Override
    DnsQuery clear(DnsSection section);

    @Override
    DnsQuery clear();

    @Override
    DnsQuery touch();

    @Override
    DnsQuery touch(Object hint);

    @Override
    DnsQuery retain();

    @Override
    DnsQuery retain(int increment);
}
