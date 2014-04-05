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

package io.netty.handler.codec.http2.draft10.frame.encoder;

import static io.netty.handler.codec.http2.draft10.Http2Exception.protocolError;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.frame.Http2DataFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2HeadersFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2PushPromiseFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2Frame;
import io.netty.handler.codec.http2.draft10.frame.Http2GoAwayFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2PingFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2PriorityFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2RstStreamFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2SettingsFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2WindowUpdateFrame;

/**
 * A composite {@link Http2FrameMarshaller} that supports all frames identified by the HTTP2 spec.
 * This handles marshalling for the following frame types:
 * <p/>
 * {@link Http2DataFrame} <br>
 * {@link Http2GoAwayFrame} <br>
 * {@link Http2HeadersFrame} <br>
 * {@link Http2PingFrame} <br>
 * {@link Http2PriorityFrame} <br>
 * {@link Http2PushPromiseFrame} <br>
 * {@link Http2RstStreamFrame} <br>
 * {@link Http2SettingsFrame} <br>
 * {@link Http2WindowUpdateFrame} <br>
 */
public class Http2StandardFrameMarshaller implements Http2FrameMarshaller {

    private final Http2FrameMarshaller dataMarshaller;
    private final Http2FrameMarshaller headersMarshaller;
    private final Http2FrameMarshaller goAwayMarshaller;
    private final Http2FrameMarshaller pingMarshaller;
    private final Http2FrameMarshaller priorityMarshaller;
    private final Http2FrameMarshaller pushPromiseMarshaller;
    private final Http2FrameMarshaller rstStreamMarshaller;
    private final Http2FrameMarshaller settingsMarshaller;
    private final Http2FrameMarshaller windowUpdateMarshaller;

    public Http2StandardFrameMarshaller() {
        this(new DefaultHttp2HeadersEncoder());
    }

    public Http2StandardFrameMarshaller(Http2HeadersEncoder headersEncoder) {
        dataMarshaller = new Http2DataFrameMarshaller();
        headersMarshaller = new Http2HeadersFrameMarshaller(headersEncoder);
        goAwayMarshaller = new Http2GoAwayFrameMarshaller();
        pingMarshaller = new Http2PingFrameMarshaller();
        priorityMarshaller = new Http2PriorityFrameMarshaller();
        pushPromiseMarshaller = new Http2PushPromiseFrameMarshaller(headersEncoder);
        rstStreamMarshaller = new Http2RstStreamFrameMarshaller();
        settingsMarshaller = new Http2SettingsFrameMarshaller();
        windowUpdateMarshaller = new Http2WindowUpdateFrameMarshaller();
    }

    @Override
    public void marshall(Http2Frame frame, ByteBuf out, ByteBufAllocator alloc)
            throws Http2Exception {
        Http2FrameMarshaller marshaller = null;

        if (frame == null) {
            throw new IllegalArgumentException("frame must be non-null");
        }

        if (frame instanceof Http2DataFrame) {
            marshaller = dataMarshaller;
        } else if (frame instanceof Http2HeadersFrame) {
            marshaller = headersMarshaller;
        } else if (frame instanceof Http2GoAwayFrame) {
            marshaller = goAwayMarshaller;
        } else if (frame instanceof Http2PingFrame) {
            marshaller = pingMarshaller;
        } else if (frame instanceof Http2PriorityFrame) {
            marshaller = priorityMarshaller;
        } else if (frame instanceof Http2PushPromiseFrame) {
            marshaller = pushPromiseMarshaller;
        } else if (frame instanceof Http2RstStreamFrame) {
            marshaller = rstStreamMarshaller;
        } else if (frame instanceof Http2SettingsFrame) {
            marshaller = settingsMarshaller;
        } else if (frame instanceof Http2WindowUpdateFrame) {
            marshaller = windowUpdateMarshaller;
        }

        if (marshaller == null) {
            throw protocolError("Unsupported frame type: %s", frame.getClass().getName());
        }

        marshaller.marshall(frame, out, alloc);
    }
}
