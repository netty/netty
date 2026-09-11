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

import io.netty.util.CharsetUtil;

/**
 * A DNS {@code NAPTR} record as defined in <a href="https://www.rfc-editor.org/rfc/rfc3403">RFC 3403</a>.
 * <p>
 * The flags, services, and regexp fields are defined as character-strings per RFC 1035 Section 3.3,
 * which specifies them as binary data ("treated as binary information") with no character encoding.
 * RFC 1035 predates Unicode and was designed for 8-bit byte sequences.
 * <p>
 * In practice, RFC 3403 defines these fields with ASCII-compatible semantics:
 * <ul>
 *   <li><b>flags</b> - single alphabetic characters (case-insensitive)</li>
 *   <li><b>services</b> - ASCII service identifiers like "E2U+sip"</li>
 *   <li><b>regexp</b> - POSIX extended regular expression (typically ASCII, though the RFC
 *       does not explicitly restrict it)</li>
 * </ul>
 * <p>
 * The raw byte array methods ({@link #flags()}, {@link #services()}, {@link #regexp()}) provide
 * access to the original binary data, allowing callers to apply their own encoding interpretation.
 * Convenience methods ({@link #flagsAsString()}, {@link #servicesAsString()}, {@link #regexpAsString()})
 * are provided for the common case of US-ASCII encoded strings.
 */
public interface DnsNaptrRecord extends DnsRecord {

    /**
     * Returns the order.
     */
    int order();

    /**
     * Returns the preference.
     */
    int preference();

    /**
     * Returns the flags field as raw bytes.
     */
    byte[] flags();

    /**
     * Returns the flags field as a US-ASCII string.
     */
    default String flagsAsString() {
        return new String(flags(), CharsetUtil.US_ASCII);
    }

    /**
     * Returns the services field as raw bytes.
     */
    byte[] services();

    /**
     * Returns the services field as a US-ASCII string.
     */
    default String servicesAsString() {
        return new String(services(), CharsetUtil.US_ASCII);
    }

    /**
     * Returns the regular expression field as raw bytes.
     */
    byte[] regexp();

    /**
     * Returns the regular expression field as a US-ASCII string.
     */
    default String regexpAsString() {
        return new String(regexp(), CharsetUtil.US_ASCII);
    }

    /**
     * Returns the replacement domain name.
     */
    String replacement();
}
