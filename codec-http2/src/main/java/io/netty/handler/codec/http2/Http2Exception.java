/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

/**
 * Exception thrown when an HTTP2 error was encountered.
 */
public class Http2Exception extends Exception {
    private static final long serialVersionUID = -2292608019080068769L;

    private final Http2Error error;

    public Http2Exception(Http2Error error) {
        this.error = error;
    }

    public Http2Exception(Http2Error error, String message) {
        super(message);
        this.error = error;
    }

    public Http2Exception(Http2Error error, String message, Throwable cause) {
        super(message, cause);
        this.error = error;
    }

    public Http2Error error() {
        return error;
    }

    public static Http2Exception format(Http2Error error, String fmt, Object... args) {
        return new Http2Exception(error, String.format(fmt, args));
    }

    public static Http2Exception protocolError(String fmt, Object... args) {
        return format(Http2Error.PROTOCOL_ERROR, fmt, args);
    }

    public static Http2Exception flowControlError(String fmt, Object... args) {
        return format(Http2Error.FLOW_CONTROL_ERROR, fmt, args);
    }
}
