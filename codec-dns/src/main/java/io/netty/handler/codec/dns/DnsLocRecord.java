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

/**
 * A DNS {@code LOC} record.
 */
public interface DnsLocRecord extends DnsRecord {

    /**
     * Returns the LOC version.
     */
    int version();

    /**
     * Returns the size field.
     */
    int size();

    /**
     * Returns the horizontal precision field.
     */
    int horizontalPrecision();

    /**
     * Returns the vertical precision field.
     */
    int verticalPrecision();

    /**
     * Returns the latitude (unsigned 32-bit value).
     */
    long latitude();

    /**
     * Returns the longitude (unsigned 32-bit value).
     */
    long longitude();

    /**
     * Returns the altitude (unsigned 32-bit value).
     */
    long altitude();
}
