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
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;

/**
 * Encodes the requested <a href="http://code.google.com/p/protobuf/">Google
 * Protocol Buffers</a> {@link Message} and {@link MessageLite} into a
 * {@link ChannelBuffer}. A typical setup for TCP/IP would be:
 * <pre>
 * {@link ChannelPipeline} pipeline = ...;
 *
 * // Decoders
 * pipeline.addLast("frameDecoder",
 *                  new {@link LengthFieldBasedFrameDecoder}(1048576, 0, 4, 0, 4));
 * pipeline.addLast("protobufDecoder",
 *                  new {@link ProtobufDecoder}(MyMessage.getDefaultInstance()));
 *
 * // Encoder
 * pipeline.addLast("frameEncoder", new {@link LengthFieldPrepender}(4));
 * pipeline.addLast("protobufEncoder", new {@link ProtobufEncoder}());
 * </pre>
 * and then you can use a {@code MyMessage} instead of a {@link ChannelBuffer}
 * as a message:
 * <pre>
 * void messageReceived({@link ChannelHandlerContext} ctx, {@link MessageEvent} e) {
 *     MyMessage req = (MyMessage) e.getMessage();
 *     MyMessage res = MyMessage.newBuilder().setText(
 *                               "Did you say '" + req.getText() + "'?").build();
 *     ch.write(res);
 * }
 * </pre>
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev:231 $, $Date:2008-06-12 16:44:50 +0900 (목, 12 6월 2008) $
 *
 * @apiviz.landmark
 */
@Sharable
public class ProtobufEncoder extends OneToOneEncoder {

    /**
     * Creates a new instance.
     */
    public ProtobufEncoder() {
        super();
    }

    @Override
    protected Object encode(
            ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        if (!(msg instanceof MessageLite)) {
            return msg;
        }
        return wrappedBuffer(((MessageLite) msg).toByteArray());
    }
}
