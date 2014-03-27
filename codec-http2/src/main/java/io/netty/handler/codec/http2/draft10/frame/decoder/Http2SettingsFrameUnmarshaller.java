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
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_TYPE_SETTINGS;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.MAX_FRAME_PAYLOAD_LENGTH;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.SETTINGS_ENABLE_PUSH;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.SETTINGS_HEADER_TABLE_SIZE;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.SETTINGS_INITIAL_WINDOW_SIZE;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.SETTINGS_MAX_CONCURRENT_STREAMS;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.frame.DefaultHttp2SettingsFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2Frame;
import io.netty.handler.codec.http2.draft10.frame.Http2FrameHeader;

/**
 * An unmarshaller for {@link Http2SettingsFrame} instances.
 */
public class Http2SettingsFrameUnmarshaller extends AbstractHttp2FrameUnmarshaller {

  @Override
  protected void validate(Http2FrameHeader frameHeader) throws Http2Exception {
    if (frameHeader.getType() != FRAME_TYPE_SETTINGS) {
      throw protocolError("Unsupported frame type: %d.", frameHeader.getType());
    }
    if (frameHeader.getStreamId() != 0) {
      throw protocolError("A stream ID must be zero.");
    }
    if (frameHeader.getFlags().isAck() && frameHeader.getPayloadLength() > 0) {
      throw protocolError("Ack settings frame must have an empty payload.");
    }
    if (frameHeader.getPayloadLength() % 5 > 0) {
      throw protocolError("Frame length %d invalid.", frameHeader.getPayloadLength());
    }
    if (frameHeader.getPayloadLength() > MAX_FRAME_PAYLOAD_LENGTH) {
      throw protocolError("Frame length %d too big.", frameHeader.getPayloadLength());
    }
  }

  @Override
  protected Http2Frame doUnmarshall(Http2FrameHeader header, ByteBuf payload,
      ByteBufAllocator alloc) throws Http2Exception {
    DefaultHttp2SettingsFrame.Builder builder = new DefaultHttp2SettingsFrame.Builder();
    builder.setAck(header.getFlags().isAck());

    int numSettings = header.getPayloadLength() / 5;
    for (int index = 0; index < numSettings; ++index) {
      short id = payload.readUnsignedByte();
      long value = payload.readUnsignedInt();
      switch (id) {
        case SETTINGS_HEADER_TABLE_SIZE:
          if (value <= 0L || value > Integer.MAX_VALUE) {
            throw protocolError("Invalid header table size setting: %d", value);
          }
          builder.setHeaderTableSize((int) value);
          break;
        case SETTINGS_ENABLE_PUSH:
          if (value != 0L && value != 1L) {
            throw protocolError("Invalid enable push setting: %d", value);
          }
          builder.setPushEnabled(value == 1);
          break;
        case SETTINGS_MAX_CONCURRENT_STREAMS:
          if (value < 0L) {
            throw protocolError("Invalid max concurrent streams setting: %d", value);
          }
          builder.setMaxConcurrentStreams(value);
          break;
        case SETTINGS_INITIAL_WINDOW_SIZE:
          if (value < 0L || value > Integer.MAX_VALUE) {
            throw protocolError("Invalid initial window size setting: %d", value);
          }
          builder.setInitialWindowSize((int) value);
          break;
        default:
          throw protocolError("Unsupported setting: %d", id);
      }
    }

    return builder.build();
  }
}
