/*
 * Copyright 2023 The Netty Project
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

/**
 * {@link Exception} that can be used to wrap some {@link Throwable} and fire it through the pipeline.
 * The {@link Http2MultiplexHandler} will unwrap the original {@link Throwable} and fire it to all its
 * active {@link Http2StreamChannel}.
 */
public final class Http2MultiplexActiveStreamsException extends Exception {

    public Http2MultiplexActiveStreamsException(Throwable cause) {
        super(cause);
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
