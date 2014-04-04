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

import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_HEADER_LENGTH;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_TYPE_SETTINGS;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.SETTINGS_ENABLE_PUSH;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.SETTINGS_HEADER_TABLE_SIZE;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.SETTINGS_INITIAL_WINDOW_SIZE;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.SETTINGS_MAX_CONCURRENT_STREAMS;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.writeUnsignedInt;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil;
import io.netty.handler.codec.http2.draft10.frame.Http2SettingsFrame;

public class Http2SettingsFrameMarshaller extends AbstractHttp2FrameMarshaller<Http2SettingsFrame> {

  public Http2SettingsFrameMarshaller() {
    super(Http2SettingsFrame.class);
  }

  @Override
  protected void doMarshall(Http2SettingsFrame frame, ByteBuf out, ByteBufAllocator alloc) {
    int numSettings = 0;
    numSettings += frame.getPushEnabled() != null ? 1 : 0;
    numSettings += frame.getHeaderTableSize() != null ? 1 : 0;
    numSettings += frame.getInitialWindowSize() != null ? 1 : 0;
    numSettings += frame.getMaxConcurrentStreams() != null ? 1 : 0;

    // Write the frame header.
    int payloadLength = 5 * numSettings;
    out.ensureWritable(FRAME_HEADER_LENGTH + payloadLength);
    out.writeShort(payloadLength);
    out.writeByte(FRAME_TYPE_SETTINGS);
    out.writeByte(frame.isAck() ? Http2FrameCodecUtil.FLAG_ACK : 0);
    out.writeInt(0);

    if (frame.getPushEnabled() != null) {
      out.writeByte(SETTINGS_ENABLE_PUSH);
      writeUnsignedInt(frame.getPushEnabled() ? 1L : 0L, out);
    }
    if (frame.getHeaderTableSize() != null) {
      out.writeByte(SETTINGS_HEADER_TABLE_SIZE);
      writeUnsignedInt(frame.getHeaderTableSize(), out);
    }
    if (frame.getInitialWindowSize() != null) {
      out.writeByte(SETTINGS_INITIAL_WINDOW_SIZE);
      writeUnsignedInt(frame.getInitialWindowSize(), out);
    }
    if (frame.getMaxConcurrentStreams() != null) {
      out.writeByte(SETTINGS_MAX_CONCURRENT_STREAMS);
      writeUnsignedInt(frame.getMaxConcurrentStreams(), out);
    }
  }
}
