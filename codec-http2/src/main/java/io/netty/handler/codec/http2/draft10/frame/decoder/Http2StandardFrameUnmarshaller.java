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

package io.netty.handler.codec.http2.draft10.frame.decoder;

import static io.netty.handler.codec.http2.draft10.Http2Exception.protocolError;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_TYPE_CONTINUATION;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_TYPE_DATA;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_TYPE_GO_AWAY;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_TYPE_HEADERS;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_TYPE_PING;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_TYPE_PRIORITY;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_TYPE_PUSH_PROMISE;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_TYPE_RST_STREAM;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_TYPE_SETTINGS;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_TYPE_WINDOW_UPDATE;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.frame.Http2Frame;
import io.netty.handler.codec.http2.draft10.frame.Http2FrameHeader;

/**
 * A composite {@link Http2FrameUnmarshaller} that supports all frames identified by the HTTP2 spec.
 * This unmarshalls the following frames:
 * <p/>
 * {@link Http2DataFrame} (buffer is a slice of input buffer - must be copied if persisted)<br>
 * {@link Http2GoAwayFrame} (buffer is a slice of input buffer - must be copied if persisted)<br>
 * {@link Http2HeadersFrame}<br>
 * {@link Http2PingFrame} (buffer is a slice of input buffer - must be copied if persisted)<br>
 * {@link Http2PriorityFrame}<br>
 * {@link Http2PushPromiseFrame}<br>
 * {@link Http2RstStreamFrame}<br>
 * {@link Http2SettingsFrame}<br>
 * {@link Http2WindowUpdateFrame}<br>
 */
public class Http2StandardFrameUnmarshaller implements Http2FrameUnmarshaller {

    private final Http2FrameUnmarshaller[] unmarshallers;
    private Http2FrameUnmarshaller activeUnmarshaller;

    public Http2StandardFrameUnmarshaller() {
        this(new DefaultHttp2HeadersDecoder());
    }

    public Http2StandardFrameUnmarshaller(Http2HeadersDecoder headersDecoder) {
        unmarshallers = new Http2FrameUnmarshaller[FRAME_TYPE_CONTINUATION + 1];
        unmarshallers[FRAME_TYPE_DATA] = new Http2DataFrameUnmarshaller();
        unmarshallers[FRAME_TYPE_HEADERS] = new Http2HeadersFrameUnmarshaller(headersDecoder);
        unmarshallers[FRAME_TYPE_PRIORITY] = new Http2PriorityFrameUnmarshaller();
        unmarshallers[FRAME_TYPE_RST_STREAM] = new Http2RstStreamFrameUnmarshaller();
        unmarshallers[FRAME_TYPE_SETTINGS] = new Http2SettingsFrameUnmarshaller();
        unmarshallers[FRAME_TYPE_PUSH_PROMISE] = new Http2PushPromiseFrameUnmarshaller(headersDecoder);
        unmarshallers[FRAME_TYPE_PING] = new Http2PingFrameUnmarshaller();
        unmarshallers[FRAME_TYPE_GO_AWAY] = new Http2GoAwayFrameUnmarshaller();
        unmarshallers[FRAME_TYPE_WINDOW_UPDATE] = new Http2WindowUpdateFrameUnmarshaller();
        unmarshallers[FRAME_TYPE_CONTINUATION] = new Http2FrameUnmarshaller() {
            private String msg = "Received continuation without headers or push_promise";

            @Override
            public Http2FrameUnmarshaller unmarshall(Http2FrameHeader header) throws Http2Exception {
                throw protocolError(msg);
            }

            @Override
            public Http2Frame from(ByteBuf payload, ByteBufAllocator alloc) throws Http2Exception {
                throw protocolError(msg);
            }
        };
    }

    @Override
    public Http2FrameUnmarshaller unmarshall(Http2FrameHeader header) throws Http2Exception {
        // If we're not in the middle of unmarshalling a continued frame (e.g. headers,
        // push_promise), select the appropriate marshaller for the frame type.
        if (activeUnmarshaller == null) {
            int type = header.getType();
            if (type < 0 || type >= unmarshallers.length || unmarshallers[type] == null) {
                throw protocolError("Unsupported frame type: %d", type);
            }

            activeUnmarshaller = unmarshallers[type];
        }

        // Prepare the unmarshaller.
        activeUnmarshaller.unmarshall(header);
        return this;
    }

    @Override
    public Http2Frame from(ByteBuf payload, ByteBufAllocator alloc) throws Http2Exception {
        if (activeUnmarshaller == null) {
            throw new IllegalStateException("Must call unmarshall() before calling from().");
        }
        Http2Frame frame = activeUnmarshaller.from(payload, alloc);
        if (frame != null) {
            // The unmarshall is complete and does not require more frames. Clear the active
            // marshaller so that we select a fresh marshaller next time.
            activeUnmarshaller = null;
        }
        return frame;
    }
}
