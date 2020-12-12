/*
 * Copyright 2020 The Netty Project
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
package io.netty.incubator.codec.http3;

public final class Http3Constants {

    private Http3Constants() { }

    /**
     * See <a href="https://tools.ietf.org/html/draft-ietf-quic-qpack-19#section-5">
     *     SETTINGS_QPACK_MAX_TABLE_CAPACITY</a>.
     */
    public static final long HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY = 0x1;

    /**
     * See <a href="https://tools.ietf.org/html/draft-ietf-quic-qpack-19#section-5">
     *     SETTINGS_QPACK_BLOCKED_STREAMS</a>.
     */
    public static final long HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS = 0x7;

    /**
     * See <a href="https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.4.1">
     *     SETTINGS_MAX_FIELD_SECTION_SIZE</a>.
     */
    public static final long HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE = 0x6;

}
