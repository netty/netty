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
package org.jboss.netty.handler.codec.serialization;

import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;

/**
 * A decoder which deserializes the received {@link ChannelBuffer}s into Java
 * objects.
 * <p>
 * Please note that the serialized form this decoder expects is not
 * compatible with the standard {@link ObjectOutputStream}.  Please use
 * {@link ObjectEncoder} or {@link ObjectEncoderOutputStream} to ensure the
 * interoperability with this decoder.
 *
 * @apiviz.landmark
 * @apiviz.has org.jboss.netty.handler.codec.serialization.ObjectDecoderInputStream - - - compatible with
 */
public class ObjectDecoder extends LengthFieldBasedFrameDecoder {

    private final ClassResolver classResolver;

    /**
     * Creates a new decoder whose maximum object size is {@code 1048576}
     * bytes.  If the size of the received object is greater than
     * {@code 1048576} bytes, a {@link StreamCorruptedException} will be
     * raised.
     * 
     * @deprecated use {@link #ObjectDecoder(ClassResolver)}
     */
    @Deprecated
    public ObjectDecoder() {
        this(1048576);
    }


    /**
     * Creates a new decoder whose maximum object size is {@code 1048576}
     * bytes.  If the size of the received object is greater than
     * {@code 1048576} bytes, a {@link StreamCorruptedException} will be
     * raised.
     * 
     * @param classResolver  the {@link ClassResolver} to use for this decoder
     */
    public ObjectDecoder(ClassResolver classResolver) {
        this(1048576, classResolver);
    }
    
    /**
     * Creates a new decoder with the specified maximum object size.
     *
     * @param maxObjectSize  the maximum byte length of the serialized object.
     *                       if the length of the received object is greater
     *                       than this value, {@link StreamCorruptedException}
     *                       will be raised.
     * @deprecated           use {@link #ObjectDecoder(int, ClassResolver)}
     */
    @Deprecated
    public ObjectDecoder(int maxObjectSize) {
        this(maxObjectSize, ClassResolvers.weakCachingResolver(null));
    }

    /**
     * Creates a new decoder with the specified maximum object size.
     *
     * @param maxObjectSize  the maximum byte length of the serialized object.
     *                       if the length of the received object is greater
     *                       than this value, {@link StreamCorruptedException}
     *                       will be raised.
     * @param classResolver    the {@link ClassResolver} which will load the class
     *                       of the serialized object
     */
    public ObjectDecoder(int maxObjectSize, ClassResolver classResolver) {
        super(maxObjectSize, 0, 4, 0, 4);
        this.classResolver = classResolver;
    }


    /**
     * Create a new decoder with the specified maximum object size and the {@link ClassLoader} wrapped in {@link ClassResolvers#weakCachingResolver(ClassLoader)}
     *
     * @param maxObjectSize  the maximum byte length of the serialized object.
     *                       if the length of the received object is greater
     *                       than this value, {@link StreamCorruptedException}
     *                       will be raised.
     * @param classLoader    the the classloader to use
     * @deprecated           use {@link #ObjectDecoder(int, ClassResolver)}
     */
    @Deprecated
    public ObjectDecoder(int maxObjectSize, ClassLoader classLoader) {
        this(maxObjectSize, ClassResolvers.weakCachingResolver(classLoader));
    }
    
    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) throws Exception {

        ChannelBuffer frame = (ChannelBuffer) super.decode(ctx, channel, buffer);
        if (frame == null) {
            return null;
        }

        return new CompactObjectInputStream(
                new ChannelBufferInputStream(frame), classResolver).readObject();
    }

    @Override
    protected ChannelBuffer extractFrame(ChannelBuffer buffer, int index, int length) {
        return buffer.slice(index, length);
    }
}
