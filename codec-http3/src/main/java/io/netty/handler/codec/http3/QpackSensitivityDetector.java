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
package io.netty.handler.codec.http3;

/**
 * Determines whether a header field is sensitive, in which case the QPACK encoder
 * <ul>
 *   <li>MUST NOT insert it into the dynamic table, and</li>
 *   <li>MUST encode it as a literal with the "Never Indexed" ({@code N=1}) flag
 *       set as defined in
 *       <a href="https://www.rfc-editor.org/rfc/rfc9204.html#section-4.5.4">RFC 9204 4.5.4</a>
 *       through <a href="https://www.rfc-editor.org/rfc/rfc9204.html#section-4.5.7">4.5.7</a>.</li>
 * </ul>
 *
 * <p>This mirrors {@code io.netty.handler.codec.http2.Http2HeadersEncoder.SensitivityDetector}
 * from the HTTP/2 / HPACK side.</p>
 *
 * <p>Setting {@code N=1} prevents intermediaries from inserting the field into
 * their own dynamic tables, which mitigates information disclosure via
 * compression-based side channels (RFC 9204 7.1) for credentials such as
 * {@code Authorization}, {@code Cookie}, {@code Set-Cookie} and
 * {@code Proxy-Authorization}.</p>
 * If the object can be dynamically modified and shared across multiple connections it may need to be thread safe.
 */
public interface QpackSensitivityDetector {

    /**
     * Treats every header field as non-sensitive. This is the historical default
     * behaviour of the QPACK encoder and is the backward-compatible choice.
     */
    QpackSensitivityDetector NEVER_SENSITIVE = (name, value) -> false;

    /**
     * Treats every header field as sensitive.
     */
    QpackSensitivityDetector ALWAYS_SENSITIVE = (name, value) -> true;

    /**
     * Determine if a header {@code name}/{@code value} pair is sensitive.
     *
     * @param name  the header field name.
     * @param value the header field value.
     * @return {@code true} if the field is sensitive and must be encoded with
     *         {@code N=1} and excluded from the dynamic table; {@code false}
     *         otherwise.
     */
    boolean isSensitive(CharSequence name, CharSequence value);
}
