/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.redis;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBufferIndexFinder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.replay.ReplayingDecoder;

import java.io.IOException;

/**
 * {@link ReplayingDecoder} which handles Redis protocol
 * 
 *
 */
public class RedisDecoder extends ReplayingDecoder<State> {

    private static final char CR = '\r';
    private static final char LF = '\n';
    private static final char ZERO = '0';
    
    // We track the current multibulk reply in the case
    // where we do not get a complete reply in a single
    // decode invocation.
    private MultiBulkReply reply;

    /**
     * Return a byte array which contains only the content of the request. The size of the content is read from the given {@link ChannelBuffer}
     * via the {@link #readInteger(ChannelBuffer)} method
     * 
     * @param is the {@link ChannelBuffer} to read from
     * @return content 
     * @throws IOException is thrown if the line-ending is not CRLF
     */
    public static byte[] readBytes(ChannelBuffer is) throws IOException {
        int size = readInteger(is);
        if (size == -1) {
            return null;
        }
        byte[] bytes = new byte[size];
        is.readBytes(bytes, 0, size);
        int cr = is.readByte();
        int lf = is.readByte();
        if (cr != CR || lf != LF) {
            throw new IOException("Improper line ending: " + cr + ", " + lf);
        }
        return bytes;
    }

    /**
     * Read an {@link Integer} from the {@link ChannelBuffer}
     * 
     * @param is
     * @return integer
     * @throws IOException
     */
    public static int readInteger(ChannelBuffer is) throws IOException {
        int size = 0;
        int sign = 1;
        int read = is.readByte();
        if (read == '-') {
            read = is.readByte();
            sign = -1;
        }
        do {
            if (read == CR) {
                if (is.readByte() == LF) {
                    break;
                }
            }
            int value = read - ZERO;
            if (value >= 0 && value < 10) {
                size *= 10;
                size += value;
            } else {
                throw new IOException("Invalid character in integer");
            }
            read = is.readByte();
        } while (true);
        return size * sign;
    }

    @Override
    public void checkpoint() {
        super.checkpoint();
    }

    @Override
    protected Object decode(ChannelHandlerContext channelHandlerContext, Channel channel, ChannelBuffer channelBuffer, State anEnum) throws Exception {
        if (reply != null) {
            reply.read(this, channelBuffer);
            Reply ret = reply;
            reply = null;
            return ret;
        }
        int code = channelBuffer.readByte();
        switch (code) {
            case StatusReply.MARKER: {
              ChannelBuffer status = channelBuffer.readBytes(channelBuffer.bytesBefore(ChannelBufferIndexFinder.CRLF));
              channelBuffer.skipBytes(2);
              return new StatusReply(status);
            }
            case ErrorReply.MARKER: {
              ChannelBuffer error = channelBuffer.readBytes(channelBuffer.bytesBefore(ChannelBufferIndexFinder.CRLF));
              channelBuffer.skipBytes(2);
              return new ErrorReply(error);
            }
            case IntegerReply.MARKER: {
                return new IntegerReply(readInteger(channelBuffer));
            }
            case BulkReply.MARKER: {
                return new BulkReply(readBytes(channelBuffer));
            }
            case MultiBulkReply.MARKER: {
                return decodeMultiBulkReply(channelBuffer);
            }
            default: {
                throw new IOException("Unexpected character in stream: " + code);
            }
        }
    }

    private MultiBulkReply decodeMultiBulkReply(ChannelBuffer is) throws IOException {
        if (reply == null) {
            reply = new MultiBulkReply();
        }
        reply.read(this, is);
        return reply;
    }
}

enum State {

}
