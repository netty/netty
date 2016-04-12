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

import io.netty.util.internal.UnstableApi;

import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;

/**
 * This exception is thrown when there are no more stream IDs available for the current connection
 */
@UnstableApi
public class Http2NoMoreStreamIdsException extends Http2Exception {
    private static final long serialVersionUID = -7756236161274851110L;
    private static final String ERROR_MESSAGE = "No more streams can be created on this connection";

    public Http2NoMoreStreamIdsException() {
        super(PROTOCOL_ERROR, ERROR_MESSAGE, ShutdownHint.GRACEFUL_SHUTDOWN);
    }

    public Http2NoMoreStreamIdsException(Throwable cause) {
        super(PROTOCOL_ERROR, ERROR_MESSAGE, cause, ShutdownHint.GRACEFUL_SHUTDOWN);
    }
}
