/*
 * Copyright 2015 The Netty Project
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

import io.netty.util.ReferenceCounted;
import io.netty.util.internal.UnstableApi;

/**
 * The superclass which contains core information concerning a {@link DnsQuery} and a {@link DnsResponse}.
 */
@UnstableApi
public interface DnsMessage extends ReferenceCounted {

    /**
     * Returns the {@code ID} of this DNS message.
     */
    int id();

    /**
     * Sets the {@code ID} of this DNS message.
     */
    DnsMessage setId(int id);

    /**
     * Returns the {@code opCode} of this DNS message.
     */
    DnsOpCode opCode();

    /**
     * Sets the {@code opCode} of this DNS message.
     */
    DnsMessage setOpCode(DnsOpCode opCode);

    /**
     * Returns the {@code RD} (recursion desired} field of this DNS message.
     */
    boolean isRecursionDesired();

    /**
     * Sets the {@code RD} (recursion desired} field of this DNS message.
     */
    DnsMessage setRecursionDesired(boolean recursionDesired);

    /**
     * Returns the {@code Z} (reserved for future use) field of this DNS message.
     */
    int z();

    /**
     * Sets the {@code Z} (reserved for future use) field of this DNS message.
     */
    DnsMessage setZ(int z);

    /**
     * Returns the number of records in the specified {@code section} of this DNS message.
     */
    int count(DnsSection section);

    /**
     * Returns the number of records in this DNS message.
     */
    int count();

    /**
     * Returns the first record in the specified {@code section} of this DNS message.
     * When the specified {@code section} is {@link DnsSection#QUESTION}, the type of the returned record is
     * always {@link DnsQuestion}.
     *
     * @return {@code null} if this message doesn't have any records in the specified {@code section}
     */
    <T extends DnsRecord> T recordAt(DnsSection section);

    /**
     * Returns the record at the specified {@code index} of the specified {@code section} of this DNS message.
     * When the specified {@code section} is {@link DnsSection#QUESTION}, the type of the returned record is
     * always {@link DnsQuestion}.
     *
     * @throws IndexOutOfBoundsException if the specified {@code index} is out of bounds
     */
    <T extends DnsRecord> T recordAt(DnsSection section, int index);

    /**
     * Sets the specified {@code section} of this DNS message to the specified {@code record},
     * making it a single-record section. When the specified {@code section} is {@link DnsSection#QUESTION},
     * the specified {@code record} must be a {@link DnsQuestion}.
     */
    DnsMessage setRecord(DnsSection section, DnsRecord record);

    /**
     * Sets the specified {@code record} at the specified {@code index} of the specified {@code section}
     * of this DNS message. When the specified {@code section} is {@link DnsSection#QUESTION},
     * the specified {@code record} must be a {@link DnsQuestion}.
     *
     * @return the old record
     * @throws IndexOutOfBoundsException if the specified {@code index} is out of bounds
     */
    <T extends DnsRecord> T setRecord(DnsSection section, int index, DnsRecord record);

    /**
     * Adds the specified {@code record} at the end of the specified {@code section} of this DNS message.
     * When the specified {@code section} is {@link DnsSection#QUESTION}, the specified {@code record}
     * must be a {@link DnsQuestion}.
     */
    DnsMessage addRecord(DnsSection section, DnsRecord record);

    /**
     * Adds the specified {@code record} at the specified {@code index} of the specified {@code section}
     * of this DNS message. When the specified {@code section} is {@link DnsSection#QUESTION}, the specified
     * {@code record} must be a {@link DnsQuestion}.
     *
     * @throws IndexOutOfBoundsException if the specified {@code index} is out of bounds
     */
    DnsMessage addRecord(DnsSection section, int index, DnsRecord record);

    /**
     * Removes the record at the specified {@code index} of the specified {@code section} from this DNS message.
     * When the specified {@code section} is {@link DnsSection#QUESTION}, the type of the returned record is
     * always {@link DnsQuestion}.
     *
     * @return the removed record
     */
    <T extends DnsRecord> T removeRecord(DnsSection section, int index);

    /**
     * Removes all the records in the specified {@code section} of this DNS message.
     */
    DnsMessage clear(DnsSection section);

    /**
     * Removes all the records in this DNS message.
     */
    DnsMessage clear();

    @Override
    DnsMessage touch();

    @Override
    DnsMessage touch(Object hint);

    @Override
    DnsMessage retain();

    @Override
    DnsMessage retain(int increment);
}
