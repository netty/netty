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

import io.netty.util.internal.UnstableApi;

import static io.netty.util.internal.ObjectUtil.checkPositive;

/**
 * The configuration of the decoding of SVCB and HTTPS records
 * (<a href="https://www.rfc-editor.org/rfc/rfc9460.html">RFC 9460</a>): limits on what a record may hold, above
 * which the record is malformed. A decoder copies the values when it is created.
 */
@UnstableApi
public final class SvcbDecoderConfig {

    static final int DEFAULT_MAX_PARAMS = 64;
    static final int DEFAULT_MAX_ALPN_IDS = 64;
    static final int DEFAULT_MAX_HINT_ADDRESSES = 64;

    private int maxParams = DEFAULT_MAX_PARAMS;
    private int maxAlpnIds = DEFAULT_MAX_ALPN_IDS;
    private int maxHintAddresses = DEFAULT_MAX_HINT_ADDRESSES;

    /**
     * Returns the maximum number of SvcParams of a record.
     */
    public int maxParams() {
        return maxParams;
    }

    /**
     * Sets the maximum number of SvcParams of a record, 64 by default.
     *
     * @return {@code this}
     */
    public SvcbDecoderConfig maxParams(int maxParams) {
        this.maxParams = checkPositive(maxParams, "maxParams");
        return this;
    }

    /**
     * Returns the maximum number of protocol identifiers of the {@code alpn} SvcParam.
     */
    public int maxAlpnIds() {
        return maxAlpnIds;
    }

    /**
     * Sets the maximum number of protocol identifiers of the {@code alpn} SvcParam, 64 by default.
     *
     * @return {@code this}
     */
    public SvcbDecoderConfig maxAlpnIds(int maxAlpnIds) {
        this.maxAlpnIds = checkPositive(maxAlpnIds, "maxAlpnIds");
        return this;
    }

    /**
     * Returns the maximum number of addresses of the {@code ipv4hint} and {@code ipv6hint} SvcParams.
     */
    public int maxHintAddresses() {
        return maxHintAddresses;
    }

    /**
     * Sets the maximum number of addresses of the {@code ipv4hint} and {@code ipv6hint} SvcParams, 64 by default.
     *
     * @return {@code this}
     */
    public SvcbDecoderConfig maxHintAddresses(int maxHintAddresses) {
        this.maxHintAddresses = checkPositive(maxHintAddresses, "maxHintAddresses");
        return this;
    }
}
