/*
 * Copyright 2015 The Netty Project
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
package io.netty.microbench.http2;

import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_INITIAL_WINDOW_SIZE;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2RemoteFlowController;
import io.netty.handler.codec.http2.Http2Stream;

public final class NoopHttp2RemoteFlowController implements Http2RemoteFlowController {
    public static final NoopHttp2RemoteFlowController INSTANCE = new NoopHttp2RemoteFlowController();

    private NoopHttp2RemoteFlowController() { }
    @Override
    public void initialWindowSize(int newWindowSize) throws Http2Exception {
    }

    @Override
    public int initialWindowSize() {
        return MAX_INITIAL_WINDOW_SIZE;
    }

    @Override
    public int windowSize(Http2Stream stream) {
        return MAX_INITIAL_WINDOW_SIZE;
    }

    @Override
    public void incrementWindowSize(ChannelHandlerContext ctx, Http2Stream stream, int delta)
            throws Http2Exception {
    }

    @Override
    public void sendFlowControlled(ChannelHandlerContext ctx, Http2Stream stream, FlowControlled payload) {
        // Don't check size beforehand because Headers payload returns 0 all the time.
        do {
            payload.write(MAX_INITIAL_WINDOW_SIZE);
        } while (payload.size() > 0);
    }
}
