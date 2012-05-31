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

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.embedder.EncoderEmbedder;

import java.io.IOException;

import junit.framework.Assert;

import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.junit.Test;

public abstract class AbstractCompatibleMarshallingEncoderTest {

    @Test
    public void testMarshalling() throws IOException, ClassNotFoundException {
        String testObject = new String("test");

        final MarshallerFactory marshallerFactory = createMarshallerFactory();
        final MarshallingConfiguration configuration = createMarshallingConfig();

        EncoderEmbedder<ChannelBuffer> encoder = new EncoderEmbedder<ChannelBuffer>(createEncoder());

        encoder.offer(testObject);
        Assert.assertTrue(encoder.finish());

        ChannelBuffer buffer = encoder.poll();

        Unmarshaller unmarshaller = marshallerFactory.createUnmarshaller(configuration);
        unmarshaller.start(Marshalling.createByteInput(truncate(buffer).toByteBuffer()));
        String read = (String) unmarshaller.readObject();
        Assert.assertEquals(testObject, read);

        Assert.assertEquals(-1, unmarshaller.read());

        Assert.assertNull(encoder.poll());

        unmarshaller.finish();
        unmarshaller.close();
    }
    protected ChannelBuffer truncate(ChannelBuffer buf) {
        return buf;
    }

    protected ChannelHandler createEncoder() {
        return new CompatibleMarshallingEncoder(createProvider());
    }

    protected MarshallerProvider createProvider() {
        return new DefaultMarshallerProvider(createMarshallerFactory(), createMarshallingConfig());
    }

    protected abstract MarshallerFactory createMarshallerFactory();

    protected abstract MarshallingConfiguration createMarshallingConfig();

}
