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

import io.netty.handler.codec.http.HttpStatusClass;

class DefaultHttp2RequestStreamCodecState implements Http2RequestStreamCodecState {
    private enum State {
        None,
        Headers,
        FinalHeaders,
        Trailers
    }
    private State state = State.None;

    /**
     * Evaluates the passed frame.
     *
     * @param frame to evaluate.
     * @return {@code true} if the frame is valid.
     */
    boolean evaluateFrame(Http2RequestStreamFrame frame) {
        if (!(frame instanceof Http2HeadersFrame || frame instanceof Http2DataFrame)) {
            return true;
        }
        switch (state) {
            case None:
            case Headers:
                if (!(frame instanceof Http2HeadersFrame)) {
                    return false;
                }
                if (isEos(frame)) {
                    state = State.Trailers;
                } else {
                    state = isInformationalResponse((Http2HeadersFrame) frame) ? State.Headers : State.FinalHeaders;
                }
                return true;
            case FinalHeaders:
                if (frame instanceof Http2HeadersFrame) {
                    if (isInformationalResponse((Http2HeadersFrame) frame)) {
                        // Information response after final response headers
                        return false;
                    }
                    // trailers
                    state = State.Trailers;
                }
                if (isEos(frame)) {
                    state = State.Trailers;
                }
                return true;
            case Trailers:
                return false;
            default:
                throw new Error();
        }
    }

    @Override
    public boolean started() {
        return state != State.None;
    }

    @Override
    public boolean receivedFinalHeaders() {
        return started() && state != State.Headers;
    }

    @Override
    public boolean terminated() {
        return state == State.Trailers;
    }

    private static boolean isEos(Http2RequestStreamFrame frame) {
        if (frame instanceof Http2HeadersFrame) {
            return ((Http2HeadersFrame) frame).isEndStream();
        }
        if (frame instanceof Http2DataFrame) {
            return ((Http2DataFrame) frame).isEndStream();
        }
        return false;
    }

    private static boolean isInformationalResponse(Http2HeadersFrame headersFrame) {
        return HttpStatusClass.valueOf(headersFrame.headers().status()) == HttpStatusClass.INFORMATIONAL;
    }
}
