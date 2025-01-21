/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.util.internal.StringUtil;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * The default {@link Http2ResetFrame} implementation.
 */
public final class DefaultHttp2ResetFrame extends AbstractHttp2StreamFrame implements Http2ResetFrame {

    private final long errorCode;

    /**
     * Construct a reset message.
     *
     * @param error the non-{@code null} reason for reset
     */
    public DefaultHttp2ResetFrame(Http2Error error) {
        errorCode = checkNotNull(error, "error").code();
    }

    /**
     * Construct a reset message.
     *
     * @param errorCode the reason for reset
     */
    public DefaultHttp2ResetFrame(long errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    public DefaultHttp2ResetFrame stream(Http2FrameStream stream) {
        super.stream(stream);
        return this;
    }

    @Override
    public String name() {
        return "RST_STREAM";
    }

    @Override
    public long errorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "(stream=" + stream() + ", errorCode=" + errorCode + ')';
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultHttp2ResetFrame)) {
            return false;
        }
        DefaultHttp2ResetFrame other = (DefaultHttp2ResetFrame) o;
        return super.equals(o) && errorCode == other.errorCode;
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = hash * 31 + (int) (errorCode ^ errorCode >>> 32);
        return hash;
    }
}
