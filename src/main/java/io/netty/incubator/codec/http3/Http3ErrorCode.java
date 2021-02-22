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

/**
 * Different <a href="https://tools.ietf.org/html/draft-ietf-quic-http-32#section-8.1">HTTP3 error codes</a>.
 */
public enum Http3ErrorCode {

    /**
     *  No error. This is used when the connection or stream needs to be closed, but there is no error to signal.
     */
    H3_NO_ERROR(0x100),

    /**
     * Peer violated protocol requirements in a way that does not match a more specific error code,
     * or endpoint declines to use the more specific error code.
     */
    H3_GENERAL_PROTOCOL_ERROR(0x101),

    /**
     * An internal error has occurred in the HTTP stack.
     */
    H3_INTERNAL_ERROR(0x102),

    /**
     * The endpoint detected that its peer created a stream that it will not accept.
     */
    H3_STREAM_CREATION_ERROR(0x103),

    /**
     * A stream required by the HTTP/3 connection was closed or reset.
     */
    H3_CLOSED_CRITICAL_STREAM(0x104),

    /**
     * A frame was received that was not permitted in the current state or on the current stream.
     */
    H3_FRAME_UNEXPECTED(0x105),

    /**
     * A frame that fails to satisfy layout requirements or with an invalid size was received.
     */
    H3_FRAME_ERROR(0x106),

    /**
     * The endpoint detected that its peer is exhibiting a behavior that might be generating excessive load.
     */
    H3_EXCESSIVE_LOAD(0x107),

    /**
     * A Stream ID or Push ID was used incorrectly, such as exceeding a limit, reducing a limit, or being reused.
     */
    H3_ID_ERROR(0x108),

    /**
     * An endpoint detected an error in the payload of a SETTINGS frame.
     */
    H3_SETTINGS_ERROR(0x109),

    /**
     * No SETTINGS frame was received at the beginning of the control stream.
     */
    H3_MISSING_SETTINGS(0x10a),

    /**
     * A server rejected a request without performing any application processing.
     */
    H3_REQUEST_REJECTED(0x10b),

    /**
     * The request or its response (including pushed response) is cancelled.
     */
    H3_REQUEST_CANCELLED(0x10c),

    /**
     * The client's stream terminated without containing a fully-formed request.
     */
    H3_REQUEST_INCOMPLETE(0x10d),

    /**
     * An HTTP message was malformed and cannot be processed.
     */
    H3_MESSAGE_ERROR(0x10e),

    /**
     * The TCP connection established in response to a CONNECT request was reset or abnormally closed.
     */
    H3_CONNECT_ERROR(0x10f),

    /**
     * The requested operation cannot be served over HTTP/3. The peer should retry over HTTP/1.1.
     */
    H3_VERSION_FALLBACK(0x110),

    /**
     * The decoder failed to interpret an encoded field section and is not able to continue decoding that field section.
     */
    QPACK_DECOMPRESSION_FAILED(0x200),

    /**
     * The decoder failed to interpret an encoder instruction received on the encoder stream.
     */
    QPACK_ENCODER_STREAM_ERROR(0x201),

    /**
     * The encoder failed to interpret a decoder instruction received on the decoder stream.
     */
    QPACK_DECODER_STREAM_ERROR(0x202);

    final int code;

    Http3ErrorCode(int code) {
        this.code = code;
    }
}
