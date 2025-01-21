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

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * An HTTP/2 exception for a specific {@link Http2FrameStream}.
 */
public final class Http2FrameStreamException extends Exception {

    private static final long serialVersionUID = -4407186173493887044L;

    private final Http2Error error;
    private final Http2FrameStream stream;

    public Http2FrameStreamException(Http2FrameStream stream, Http2Error error, Throwable cause) {
        super(cause.getMessage(), cause);
        this.stream = checkNotNull(stream, "stream");
        this.error = checkNotNull(error, "error");
    }

    public Http2Error error() {
        return error;
    }

    public Http2FrameStream stream() {
        return stream;
    }
}
