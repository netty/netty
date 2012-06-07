/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.marshalling;

import static org.junit.Assert.*;
import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedStreamChannel;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.TooLongFrameException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import junit.framework.Assert;

import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.junit.Test;

public abstract class AbstractCompatibleMarshallingDecoderTest {
    private final String testObject = new String("test");

    @Test
    public void testSimpleUnmarshalling() throws IOException {
        MarshallerFactory marshallerFactory = createMarshallerFactory();
        MarshallingConfiguration configuration = createMarshallingConfig();

        EmbeddedStreamChannel ch = new EmbeddedStreamChannel(createDecoder(Integer.MAX_VALUE));

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        Marshaller marshaller = marshallerFactory.createMarshaller(configuration);
        marshaller.start(Marshalling.createByteOutput(bout));
        marshaller.writeObject(testObject);
        marshaller.finish();
        marshaller.close();

        byte[] testBytes = bout.toByteArray();

        ch.writeInbound(input(testBytes));
        assertTrue(ch.finish());

        String unmarshalled = (String) ch.readInbound();

        Assert.assertEquals(testObject, unmarshalled);

        Assert.assertNull(ch.readInbound());
    }

    protected ChannelBuffer input(byte[] input) {
        return ChannelBuffers.wrappedBuffer(input);
    }

    @Test
    public void testFragmentedUnmarshalling() throws IOException {
        MarshallerFactory marshallerFactory = createMarshallerFactory();
        MarshallingConfiguration configuration = createMarshallingConfig();

        EmbeddedStreamChannel ch = new EmbeddedStreamChannel(createDecoder(Integer.MAX_VALUE));

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        Marshaller marshaller = marshallerFactory.createMarshaller(configuration);
        marshaller.start(Marshalling.createByteOutput(bout));
        marshaller.writeObject(testObject);
        marshaller.finish();
        marshaller.close();

        byte[] testBytes = bout.toByteArray();

        ChannelBuffer buffer = input(testBytes);
        ChannelBuffer slice = buffer.readSlice(2);

        ch.writeInbound(slice);
        ch.writeInbound(buffer);
        assertTrue(ch.finish());

        String unmarshalled = (String) ch.readInbound();

        Assert.assertEquals(testObject, unmarshalled);

        Assert.assertNull(ch.readInbound());
    }

    @Test
    public void testTooBigObject() throws IOException {
        MarshallerFactory marshallerFactory = createMarshallerFactory();
        MarshallingConfiguration configuration = createMarshallingConfig();

        ChannelHandler mDecoder = createDecoder(4);
        EmbeddedStreamChannel ch = new EmbeddedStreamChannel(mDecoder);

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        Marshaller marshaller = marshallerFactory.createMarshaller(configuration);
        marshaller.start(Marshalling.createByteOutput(bout));
        marshaller.writeObject(testObject);
        marshaller.finish();
        marshaller.close();

        byte[] testBytes = bout.toByteArray();
        try {
            ch.writeInbound(input(testBytes));
            fail();
        } catch (CodecException e) {
            assertEquals(TooLongFrameException.class, e.getClass());
        }
    }

    protected ChannelHandler createDecoder(int maxObjectSize) {
        return new CompatibleMarshallingDecoder(createProvider(createMarshallerFactory(), createMarshallingConfig()), maxObjectSize);
    }

    protected UnmarshallerProvider createProvider(MarshallerFactory factory, MarshallingConfiguration config) {
        return new DefaultUnmarshallerProvider(factory, config);

    }

    protected abstract MarshallerFactory createMarshallerFactory();
    protected abstract MarshallingConfiguration createMarshallingConfig();

}
