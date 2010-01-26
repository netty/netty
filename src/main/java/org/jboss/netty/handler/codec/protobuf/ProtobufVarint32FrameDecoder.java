/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.protobuf;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.frame.FrameDecoder;

import com.google.protobuf.CodedInputStream;

/**
 * A decoder that splits the received {@link ChannelBuffer}s dynamically by the
 * value of the length field in the message. {@link ProtobufVarint32FrameDecoder}
 * should be used to decode a binary message which has an integer header field
 * encoded as Google Protocol Buffer <a href="http://code.google.com/apis/protocolbuffers/docs/encoding.html#varints">Base
 * 128 Varints</a> (32-bit) integer that represents the length of the message
 * body.
 *
 * @see com.google.protobuf.CodedInputStream
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Tomasz Blachowicz (tblachowicz@gmail.com)
 *
 * @version $Rev$, $Date$
 */
public class ProtobufVarint32FrameDecoder extends FrameDecoder {

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) throws Exception {
        buffer.markReaderIndex();
        byte[] buf = new byte[5];
        for (int i = 0; i < 5; i ++) {
            if (!buffer.readable()) {
                break;
            }

            buf[i] = buffer.readByte();
            if (buf[i] >= 0) {
                int messageSize = CodedInputStream.newInstance(buf, 0, i + 1).readRawVarint32();
                if (buffer.readableBytes() < messageSize) {
                    break;
                }

                return buffer.readBytes(messageSize);
            }
        }

        buffer.resetReaderIndex();
        return null;
    }
}
