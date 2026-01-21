/*
 * Copyright 2025 The Netty Project
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
package io.netty.handler.codec.http3;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum Http3SettingIdentifier {

    /**
     * QPACK maximum table capacity setting identifier (<b>0x1</b>).
     * <p>
     * Defined in <a href="https://datatracker.ietf.org/doc/html/rfc9204#section-5">
     * RFC 9204, Section 5 (SETTINGS_QPACK_MAX_TABLE_CAPACITY)</a> and registered in
     * the <a href="https://www.iana.org/assignments/http3-parameters/http3-parameters.xhtml#settings">
     * HTTP/3 SETTINGS registry (IANA)</a>.
     * <br>
     * Controls the maximum size of the dynamic table used by QPACK.
     */
    HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY(0x1),

    /**
     * Maximum field section size setting identifier (<b>0x6</b>).
     * <p>
     * Defined in <a href="https://datatracker.ietf.org/doc/html/rfc9114#section-7.2.4.1">
     * RFC 9114, Section 7.2.4.1 (SETTINGS_MAX_FIELD_SECTION_SIZE)</a> , also referenced
     * in the <a href="https://datatracker.ietf.org/doc/html/rfc9114#section-7.2.4.1">
     * HTTP/3 SETTINGS registry (RFC 9114, Section 7.2.4.1)</a> and registered in
     * the <a href="https://www.iana.org/assignments/http3-parameters/http3-parameters.xhtml#settings">
     * HTTP/3 SETTINGS registry (IANA)</a>.
     * <br>
     * Specifies the upper bound on the total size of HTTP field sections accepted by a peer.
     */
    HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE(0x6),

    /**
     * QPACK blocked streams setting identifier (<b>0x7</b>).
     * <p>
     * Defined in <a href="https://datatracker.ietf.org/doc/html/rfc9204#section-5">
     * RFC 9204, Section 5 (SETTINGS_QPACK_BLOCKED_STREAMS)</a> and registered in
     * the <a href="https://www.iana.org/assignments/http3-parameters/http3-parameters.xhtml#settings">
     * HTTP/3 SETTINGS registry (IANA)</a>.
     * <br>
     * Indicates the maximum number of streams that can be blocked waiting for QPACK instructions.
     */
    HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS(0x7),

    /**
     * ENABLE_CONNECT_PROTOCOL setting identifier (<b>0x8</b>).
     * <p>
     * Defined and registered in <a href="https://datatracker.ietf.org/doc/html/rfc9220#section-5">
     * RFC 9220, Section 5 (IANA Considerations)</a> and registered in
     * the <a href="https://www.iana.org/assignments/http3-parameters/http3-parameters.xhtml#settings">
     * HTTP/3 SETTINGS registry (IANA)</a>.
     * <br>
     * Enables use of the CONNECT protocol in HTTP/3 when set to 1; disabled when 0.
     */
    HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL(0x8),

    /**
     * ENABLE_H3_DATAGRAM setting identifier (<b>0x8</b>).
     * <p>
     * Defined and registered in <a href="https://datatracker.ietf.org/doc/html/rfc9297#name-http-3-setting">
     * RFC 9220, Section 5 (IANA Considerations)</a> and registered in
     * the <a href="https://www.iana.org/assignments/http3-parameters/http3-parameters.xhtml#settings">
     * HTTP/3 SETTINGS registry (IANA)</a>.
     * <br>
     * Enables use of the CONNECT protocol in HTTP/3 when set to 1; disabled when 0.
     */
    HTTP3_SETTINGS_H3_DATAGRAM(0x33);

    private final long id;

    private static final Map<Long, Http3SettingIdentifier> LOOKUP = Collections.unmodifiableMap(
        Arrays.stream(values()).collect(Collectors.toMap(Http3SettingIdentifier::id, Function.identity()))
    );

    Http3SettingIdentifier(long id) {
        this.id = id;
    }

    /**
     * Returns the Identifier of {@link Http3SettingIdentifier}
     * for example:
     * SETTINGS_QPACK_MAX_TABLE_CAPACITY = 0x1 = 1 in the settings frame
     * <br>
     * @return long(represented as hexadecimal above) value of the Identifier
     */
    public long id() {
        return id;
    }

    /**
     * Returns {@link Http3SettingIdentifier}
     * @param id
     * @return {@link Http3SettingIdentifier} enum which represents @param id, null otherwise
     */
    @Nullable
    public static Http3SettingIdentifier fromId(long id) {
        return LOOKUP.get(id);
    }
}
