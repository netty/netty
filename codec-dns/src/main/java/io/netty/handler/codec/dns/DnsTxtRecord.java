/*
 * Copyright 2026 The Netty Project
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

import java.util.List;

/**
 * A DNS {@code TXT} record.
 * <p>
 * Per RFC 1035, TXT RDATA consists of one or more character-strings which are
 * treated as binary information. Each entry in the list corresponds to one
 * character-string (up to 255 bytes each).
 */
public interface DnsTxtRecord extends DnsRecord {

    /**
     * Returns the binary content entries stored in this record.
     * Each byte array represents one character-string from the TXT RDATA.
     */
    List<byte[]> content();
}
