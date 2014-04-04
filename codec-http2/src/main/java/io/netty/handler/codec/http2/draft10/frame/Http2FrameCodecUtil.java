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

package io.netty.handler.codec.http2.draft10.frame;

import io.netty.buffer.ByteBuf;

/**
 * Constants and utility method used for encoding/decoding HTTP2 frames.
 */
public final class Http2FrameCodecUtil {
  public static final int CONNECTION_STREAM_ID = 0;

  public static final int DEFAULT_STREAM_PRIORITY = 0x40000000; // 2^30

  public static final int MAX_FRAME_PAYLOAD_LENGTH = 16383;
  public static final int PING_FRAME_PAYLOAD_LENGTH = 8;
  public static final short MAX_UNSIGNED_BYTE = 0xFF;
  public static final int MAX_UNSIGNED_SHORT = 0xFFFF;
  public static final long MAX_UNSIGNED_INT = 0xFFFFFFFFL;
  public static final int FRAME_HEADER_LENGTH = 8;
  public static final int FRAME_LENGTH_MASK = 0x3FFF;

  public static final short FRAME_TYPE_DATA = 0x0;
  public static final short FRAME_TYPE_HEADERS = 0x1;
  public static final short FRAME_TYPE_PRIORITY = 0x2;
  public static final short FRAME_TYPE_RST_STREAM = 0x3;
  public static final short FRAME_TYPE_SETTINGS = 0x4;
  public static final short FRAME_TYPE_PUSH_PROMISE = 0x5;
  public static final short FRAME_TYPE_PING = 0x6;
  public static final short FRAME_TYPE_GO_AWAY = 0x7;
  public static final short FRAME_TYPE_WINDOW_UPDATE = 0x8;
  public static final short FRAME_TYPE_CONTINUATION = 0x9;

  public static final short SETTINGS_HEADER_TABLE_SIZE = 1;
  public static final short SETTINGS_ENABLE_PUSH = 2;
  public static final short SETTINGS_MAX_CONCURRENT_STREAMS = 3;
  public static final short SETTINGS_INITIAL_WINDOW_SIZE = 4;

  public static final short FLAG_END_STREAM = 0x1;
  public static final short FLAG_END_SEGMENT = 0x2;
  public static final short FLAG_END_HEADERS = 0x4;
  public static final short FLAG_PRIORITY = 0x8;
  public static final short FLAG_ACK = 0x1;
  public static final short FLAG_PAD_LOW = 0x10;
  public static final short FLAG_PAD_HIGH = 0x20;

  /**
   * Reads a big-endian (31-bit) integer from the buffer.
   */
  public static int readUnsignedInt(ByteBuf buf) {
    int offset = buf.readerIndex();
    int value = (buf.getByte(offset + 0) & 0x7F) << 24 | (buf.getByte(offset + 1) & 0xFF) << 16
        | (buf.getByte(offset + 2) & 0xFF) << 8 | buf.getByte(offset + 3) & 0xFF;
    buf.skipBytes(4);
    return value;
  }

  /**
   * Writes a big-endian (32-bit) unsigned integer to the buffer.
   */
  public static void writeUnsignedInt(long value, ByteBuf out) {
    out.writeByte((int) ((value >> 24) & 0xFF));
    out.writeByte((int) ((value >> 16) & 0xFF));
    out.writeByte((int) ((value >> 8) & 0xFF));
    out.writeByte((int) ((value & 0xFF)));
  }

  /**
   * Reads the variable-length padding length field from the payload.
   */
  public static int readPaddingLength(Http2Flags flags, ByteBuf payload) {
    int paddingLength = 0;
    if (flags.isPadHighPresent()) {
      paddingLength += payload.readUnsignedByte() * 256;
    }
    if (flags.isPadLowPresent()) {
      paddingLength += payload.readUnsignedByte();
    }
    return paddingLength;
  }

  /**
   * Sets the padding flags in the given flags value as appropriate based on the padding length.
   * Returns the new flags value after any padding flags have been set.
   */
  public static short setPaddingFlags(short flags, int paddingLength) {
    if (paddingLength > 255) {
      flags |= Http2FrameCodecUtil.FLAG_PAD_HIGH;
    }
    if (paddingLength > 0) {
      flags |= Http2FrameCodecUtil.FLAG_PAD_LOW;
    }
    return flags;
  }

  /**
   * Writes the padding length field to the output buffer.
   */
  public static void writePaddingLength(int paddingLength, ByteBuf out) {
    if (paddingLength > 255) {
      int padHigh = paddingLength / 256;
      out.writeByte(padHigh);
    }
    if (paddingLength > 0) {
      int padLow = paddingLength % 256;
      out.writeByte(padLow);
    }
  }

  private Http2FrameCodecUtil() {
  }
}
