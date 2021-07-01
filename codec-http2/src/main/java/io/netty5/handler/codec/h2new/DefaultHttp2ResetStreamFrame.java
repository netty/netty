/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.h2new;

/**
 * Default implementation of {@link Http2ResetStreamFrame}.
 */
public final class DefaultHttp2ResetStreamFrame implements Http2ResetStreamFrame {
    private final int streamId;
    private final long errorCode;

    /**
     * Creates a new instance.
     *
     * @param streamId the identifier for the stream on which this frame was sent/received.
     * @param errorCode cause for the reset.
     */
    public DefaultHttp2ResetStreamFrame(int streamId, long errorCode) {
        this.streamId = streamId;
        this.errorCode = errorCode;
    }

    @Override
    public Type frameType() {
        return Type.RstStream;
    }

    @Override
    public int streamId() {
        return streamId;
    }

    @Override
    public long errorCode() {
        return errorCode;
    }
}
