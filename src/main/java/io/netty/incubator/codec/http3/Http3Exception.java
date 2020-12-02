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

import io.netty.util.internal.ObjectUtil;

/**
 * An exception related to violate the HTTP3 spec.
 */
public final class Http3Exception extends Exception {
    private final Http3ErrorCode errorCode;

    public Http3Exception(Http3ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = ObjectUtil.checkNotNull(errorCode, "errorCode");
    }

    public Http3Exception(Http3ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = ObjectUtil.checkNotNull(errorCode, "errorCode");
    }

    /**
     * Returns the related {@link Http3ErrorCode}.
     */
    public Http3ErrorCode errorCode() {
        return errorCode;
    }
}
