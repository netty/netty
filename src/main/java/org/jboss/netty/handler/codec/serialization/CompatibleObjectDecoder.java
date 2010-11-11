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
package org.jboss.netty.handler.codec.serialization;

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamConstants;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.replay.ReplayingDecoder;

/**
 * A decoder which deserializes the received {@link ChannelBuffer}s into Java
 * objects (interoperability version).
 * <p>
 * This decoder is interoperable with the standard Java object
 * streams such as {@link ObjectInputStream} and {@link ObjectOutputStream}.
 * <p>
 * However, this decoder might perform worse than {@link ObjectDecoder} if
 * the serialized object is big and complex.  Also, it does not limit the
 * maximum size of the object, and consequently your application might face
 * the risk of <a href="http://en.wikipedia.org/wiki/DoS">DoS attack</a>.
 * Please use {@link ObjectEncoder} and {@link ObjectDecoder} if you are not
 * required to keep the interoperability with the standard object streams.
 *
 * @deprecated This decoder has a known critical bug which fails to decode and
 *             raises a random exception in some circumstances.  Avoid to use
 *             it whenever you can. The only workaround is to replace
 *             {@link CompatibleObjectEncoder}, {@link CompatibleObjectDecoder},
 *             {@link ObjectInputStream}, and {@link ObjectOutputStream} with
 *             {@link ObjectEncoder}, {@link ObjectDecoder},
 *             {@link ObjectEncoderOutputStream}, and
 *             {@link ObjectDecoderInputStream} respectively.  This workaround
 *             requires both a client and a server to be modified.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2188 $, $Date: 2010-02-19 18:00:00 +0900 (Fri, 19 Feb 2010) $
 */
@Deprecated
public class CompatibleObjectDecoder extends ReplayingDecoder<CompatibleObjectDecoderState> {

    private final SwitchableInputStream bin = new SwitchableInputStream();
    private ObjectInputStream oin;

    /**
     * Creates a new decoder.
     */
    public CompatibleObjectDecoder() {
        super(CompatibleObjectDecoderState.READ_HEADER);
    }

    /**
     * Creates a new {@link ObjectInputStream} which wraps the specified
     * {@link InputStream}.  Override this method to use a subclass of the
     * {@link ObjectInputStream}.
     */
    protected ObjectInputStream newObjectInputStream(InputStream in) throws Exception {
        return new ObjectInputStream(in);
    }

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer, CompatibleObjectDecoderState state) throws Exception {
        bin.switchStream(new ChannelBufferInputStream(buffer));
        switch (state) {
        case READ_HEADER:
            oin = newObjectInputStream(bin);
            checkpoint(CompatibleObjectDecoderState.READ_OBJECT);
        case READ_OBJECT:
            return oin.readObject();
        default:
            throw new IllegalStateException("Unknown state: " + state);
        }
    }

    @Override
    protected Object decodeLast(ChannelHandlerContext ctx, Channel channel,
            ChannelBuffer buffer, CompatibleObjectDecoderState state)
            throws Exception {
        switch (buffer.readableBytes()) {
        case 0:
            return null;
        case 1:
            // Ignore the last TC_RESET
            if (buffer.getByte(buffer.readerIndex()) == ObjectStreamConstants.TC_RESET) {
                buffer.skipBytes(1);
                oin.close();
                return null;
            }
        }

        Object decoded =  decode(ctx, channel, buffer, state);
        oin.close();
        return decoded;
    }
}
