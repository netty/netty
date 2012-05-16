/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.marshalling;

import java.io.IOException;
import java.io.ObjectStreamConstants;

import org.jboss.marshalling.ByteInput;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import io.netty.buffer.ChannelBuffer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ExceptionEvent;
import io.netty.handler.codec.frame.TooLongFrameException;
import io.netty.handler.codec.replay.ReplayingDecoder;
import io.netty.handler.codec.replay.VoidEnum;

/**
 * {@link ReplayingDecoder} which use an {@link Unmarshaller} to read the Object out of the {@link ChannelBuffer}.
 * 
 * Most times you want to use {@link ThreadLocalMarshallingDecoder} to get a better performance and less overhead.
 * 
 *
 */
public class MarshallingDecoder extends ReplayingDecoder<VoidEnum> {
    protected final MarshallingConfiguration config;
    protected final MarshallerFactory factory;
    protected final long maxObjectSize;
    
    /**
     * Create a new instance of {@link MarshallingDecoder}. 
     * 
     * @param factory       the {@link MarshallerFactory} which is used to obtain the {@link Unmarshaller} from
     * @param config        the {@link MarshallingConfiguration} to use 
     * @param maxObjectSize the maximal size (in bytes) of the {@link Object} to unmarshal. Once the size is exceeded
     *                      the {@link Channel} will get closed. Use a a maxObjectSize of <= 0 to disable this. 
     *                      You should only do this if you are sure that the received Objects will never be big and the
     *                      sending side are trusted, as this opens the possibility for a DOS-Attack due an {@link OutOfMemoryError}.
     *                      
     */
    public MarshallingDecoder(MarshallerFactory factory, MarshallingConfiguration config, long maxObjectSize) {
        this.factory = factory;
        this.config = config;
        this.maxObjectSize = maxObjectSize;
    }
    
    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer, VoidEnum state) throws Exception {
        Unmarshaller unmarshaller = factory.createUnmarshaller(config);
        ByteInput input = new ChannelBufferByteInput(buffer);
        if (maxObjectSize > 0) {
            input = new LimitingByteInput(input, maxObjectSize);
        } 
        try {
            unmarshaller.start(input);
            Object obj = unmarshaller.readObject();
            unmarshaller.finish();
            return obj;
        } catch (LimitingByteInput.TooBigObjectException e) {
            throw new TooLongFrameException("Object to big to unmarshal");
        } finally {
            // Call close in a finally block as the ReplayingDecoder will throw an Error if not enough bytes are
            // readable. This helps to be sure that we do not leak resource
            unmarshaller.close();
        }
    }

    @Override
    protected Object decodeLast(ChannelHandlerContext ctx, Channel channel,
            ChannelBuffer buffer, VoidEnum state)
            throws Exception {
        switch (buffer.readableBytes()) {
        case 0:
            return null;
        case 1:
            // Ignore the last TC_RESET
            if (buffer.getByte(buffer.readerIndex()) == ObjectStreamConstants.TC_RESET) {
                buffer.skipBytes(1);
                return null;
            }
        }

        Object decoded = decode(ctx, channel, buffer, state);
        return decoded;
    }

    /**
     * Calls {@link Channel#close()} if a TooLongFrameException was thrown
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        if (e.getCause() instanceof TooLongFrameException) {
            e.getChannel().close();

        } else {
            super.exceptionCaught(ctx, e);
        }
    }
    
    /**
     * Create a new {@link Unmarshaller} for the given {@link Channel}
     * 
     */
    protected Unmarshaller getUnmarshaller(Channel channel) throws IOException {
        return factory.createUnmarshaller(config);
    }
}
