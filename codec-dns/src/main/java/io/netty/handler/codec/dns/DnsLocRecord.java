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
 * A DNS {@code LOC} record as defined in <a href="https://www.rfc-editor.org/rfc/rfc1876">RFC 1876</a>.
 * <p>
 * LOC records express geographic location information for a domain name, including
 * latitude, longitude, altitude, and precision values.
 */
public interface DnsLocRecord extends DnsRecord {

    /**
     * The value representing the equator for latitude (2^31 = 2147483648).
     * Values greater than this are north latitude; values less are south latitude.
     */
    long EQUATOR = 1L << 31;

    /**
     * The value representing the prime meridian for longitude (2^31 = 2147483648).
     * Values greater than this are east longitude; values less are west longitude.
     */
    long PRIME_MERIDIAN = 1L << 31;

    /**
     * The base altitude value representing 100,000 meters below the WGS 84 reference spheroid.
     * The actual altitude in centimeters above WGS 84 is {@code altitude() - ALTITUDE_BASE}.
     */
    long ALTITUDE_BASE = 10_000_000L;

    /**
     * Returns the LOC version. Must be 0 for the format defined in RFC 1876.
     */
    int version();

    /**
     * Returns the diameter of the sphere enclosing the described entity, as a raw encoded byte.
     * <p>
     * The value is encoded as a pair of 4-bit unsigned integers: the high nibble is the base (0-9),
     * and the low nibble is the exponent (0-9). The size in centimeters is {@code base * 10^exp}.
     * For example, {@code 0x12} represents {@code 1 * 10^2 = 100} centimeters (1 meter).
     */
    int size();

    /**
     * Returns the horizontal precision (diameter of the circle of error), as a raw encoded byte.
     * <p>
     * Uses the same encoding as {@link #size()}: {@code base * 10^exp} centimeters.
     */
    int horizontalPrecision();

    /**
     * Returns the vertical precision (total height of the error distribution), as a raw encoded byte.
     * <p>
     * Uses the same encoding as {@link #size()}: {@code base * 10^exp} centimeters.
     */
    int verticalPrecision();

    /**
     * Returns the latitude as an unsigned 32-bit value in thousandths of a second of arc.
     * <p>
     * The value {@link #EQUATOR} (2^31) represents the equator. Values above this are north
     * latitude; values below are south latitude. To convert to degrees:
     * <pre>{@code
     * double degrees = (latitude() - EQUATOR) / (3600.0 * 1000.0);
     * }</pre>
     */
    long latitude();

    /**
     * Returns the longitude as an unsigned 32-bit value in thousandths of a second of arc.
     * <p>
     * The value {@link #PRIME_MERIDIAN} (2^31) represents the prime meridian. Values above this
     * are east longitude; values below are west longitude. To convert to degrees:
     * <pre>{@code
     * double degrees = (longitude() - PRIME_MERIDIAN) / (3600.0 * 1000.0);
     * }</pre>
     */
    long longitude();

    /**
     * Returns the altitude as an unsigned 32-bit value in centimeters.
     * <p>
     * The value is measured from a base 100,000 meters below the WGS 84 reference spheroid.
     * To convert to meters above WGS 84:
     * <pre>{@code
     * double meters = (altitude() - ALTITUDE_BASE) / 100.0;
     * }</pre>
     */
    long altitude();
}
