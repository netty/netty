/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.serialization;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;

/**
 * A decoder which deserializes the received {@link ByteBuf}s into Java
 * objects.
 * <p>
 * Please note that the serialized form this decoder expects is not
 * compatible with the standard {@link ObjectOutputStream}.  Please use
 * {@link ObjectEncoder} or {@link ObjectEncoderOutputStream} to ensure the
 * interoperability with this decoder.
 * <p>
 * <strong>Security:</strong> serialization can be a security liability,
 * and should not be used without defining a list of classes that are
 * allowed to be desirialized. Such a list can be specified with the
 * <tt>jdk.serialFilter</tt> system property, for instance.
 * See the <a href="https://docs.oracle.com/en/java/javase/17/core/serialization-filtering1.html">
 * serialization filtering</a> article for more information.
 *
 * @deprecated This class has been deprecated with no replacement,
 * because serialization can be a security liability
 */
@Deprecated
public class ObjectDecoder extends LengthFieldBasedFrameDecoder {

    private final ClassResolver classResolver;

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
     * @param classResolver    the {@link ClassResolver} which will load the class
     *                       of the serialized object
     */
    public ObjectDecoder(int maxObjectSize, ClassResolver classResolver) {
        super(maxObjectSize, 0, 4, 0, 4);
        this.classResolver = classResolver;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }

        ObjectInputStream ois = new CompactObjectInputStream(new ByteBufInputStream(frame, true), classResolver);
        try {
            return ois.readObject();
        } finally {
            ois.close();
        }
    }
}
