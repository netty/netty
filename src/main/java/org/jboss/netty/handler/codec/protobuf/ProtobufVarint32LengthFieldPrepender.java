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

import static org.jboss.netty.buffer.ChannelBuffers.*;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

import com.google.protobuf.CodedOutputStream;

/**
 * An encoder that prepends the the Google Protocol Buffers
 * <a href="http://code.google.com/apis/protocolbuffers/docs/encoding.html#varints">Base
 * 128 Varints</a> integer length field.  For example:
 * <pre>
 * BEFORE DECODE (300 bytes)       AFTER DECODE (302 bytes)
 * +---------------+               +--------+---------------+
 * | Protobuf Data |-------------->| Length | Protobuf Data |
 * |  (300 bytes)  |               | 0xAC02 |  (300 bytes)  |
 * +---------------+               +--------+---------------+
 * </pre> *
 *
 * @see com.google.protobuf.CodedOutputStream
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Tomasz Blachowicz (tblachowicz@gmail.com)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2315 $, $Date: 2010-06-23 14:16:47 +0900 (Wed, 23 Jun 2010) $
 */
@Sharable
public class ProtobufVarint32LengthFieldPrepender extends OneToOneEncoder {

    /**
     * Creates a new instance.
     */
    public ProtobufVarint32LengthFieldPrepender() {
        super();
    }

    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel,
            Object msg) throws Exception {
        if (!(msg instanceof ChannelBuffer)) {
            return msg;
        }

        ChannelBuffer body = (ChannelBuffer) msg;
        int length = body.readableBytes();
        ChannelBuffer header =
            channel.getConfig().getBufferFactory().getBuffer(
                    body.order(),
                    CodedOutputStream.computeRawVarint32Size(length));
        CodedOutputStream codedOutputStream = CodedOutputStream
                .newInstance(new ChannelBufferOutputStream(header));
        codedOutputStream.writeRawVarint32(length);
        codedOutputStream.flush();
        return wrappedBuffer(header, body);
    }

}
