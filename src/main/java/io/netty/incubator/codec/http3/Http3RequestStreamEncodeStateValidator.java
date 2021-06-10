/*
 * Copyright 2021 The Netty Project
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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpStatusClass;

import static io.netty.incubator.codec.http3.Http3FrameValidationUtils.frameTypeUnexpected;

final class Http3RequestStreamEncodeStateValidator extends ChannelOutboundHandlerAdapter
        implements Http3RequestStreamCodecState {
    enum State {
        None,
        Headers,
        FinalHeaders,
        Trailers
    }
    private State state = State.None;

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!(msg instanceof Http3RequestStreamFrame)) {
            super.write(ctx, msg, promise);
            return;
        }
        final Http3RequestStreamFrame frame = (Http3RequestStreamFrame) msg;
        final State nextState = evaluateFrame(state, frame);
        if (nextState == null) {
            frameTypeUnexpected(ctx, msg);
            return;
        }
        state = nextState;
        super.write(ctx, msg, promise);
    }

    @Override
    public boolean started() {
        return isStreamStarted(state);
    }

    @Override
    public boolean receivedFinalHeaders() {
        return isFinalHeadersReceived(state);
    }

    @Override
    public boolean terminated() {
        return isTrailersReceived(state);
    }

    /**
     * Evaluates the passed frame and returns the following:
     * <ul>
     *     <li>Modified {@link State} if the state should be changed.</li>
     *     <li>Same {@link State} as the passed {@code state} if no state change is necessary</li>
     *     <li>{@code null} if the frame is unexpected</li>
     * </ul>
     *
     * @param state Current state.
     * @param frame to evaluate.
     * @return Next {@link State} or {@code null} if the frame is invalid.
     */
    static State evaluateFrame(State state, Http3RequestStreamFrame frame) {
        if (frame instanceof Http3PushPromiseFrame || frame instanceof Http3UnknownFrame) {
            // always allow push promise frames.
            return state;
        }
        switch (state) {
            case None:
            case Headers:
                if (!(frame instanceof Http3HeadersFrame)) {
                    return null;
                }
                return isInformationalResponse((Http3HeadersFrame) frame) ? State.Headers : State.FinalHeaders;
            case FinalHeaders:
                if (frame instanceof Http3HeadersFrame) {
                    if (isInformationalResponse((Http3HeadersFrame) frame)) {
                        // Information response after final response headers
                        return null;
                    }
                    // trailers
                    return State.Trailers;
                }
                return state;
            case Trailers:
                return null;
            default:
                throw new Error();
        }
    }

    static boolean isStreamStarted(State state) {
        return state != State.None;
    }

    static boolean isFinalHeadersReceived(State state) {
        return isStreamStarted(state) && state != State.Headers;
    }

    static boolean isTrailersReceived(State state) {
        return state == State.Trailers;
    }

    private static boolean isInformationalResponse(Http3HeadersFrame headersFrame) {
        return HttpStatusClass.valueOf(headersFrame.headers().status()) == HttpStatusClass.INFORMATIONAL;
    }
}
