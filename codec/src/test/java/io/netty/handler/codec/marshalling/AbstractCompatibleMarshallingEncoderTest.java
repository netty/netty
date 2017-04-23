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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.junit.Test;

import static org.junit.Assert.*;

public abstract class AbstractCompatibleMarshallingEncoderTest extends AbstractMarshallingTest {

    @Test
    public void testMarshalling() throws Exception {
        @SuppressWarnings("RedundantStringConstructorCall")
        String testObject = new String("test");

        final MarshallerFactory marshallerFactory = createMarshallerFactory();
        final MarshallingConfiguration configuration = createMarshallingConfig();

        EmbeddedChannel ch = new EmbeddedChannel(createEncoder());

        ch.writeOutbound(testObject);
        assertTrue(ch.finish());

        ByteBuf buffer = ch.readOutbound();

        Unmarshaller unmarshaller = marshallerFactory.createUnmarshaller(configuration);
        unmarshaller.start(Marshalling.createByteInput(truncate(buffer).nioBuffer()));
        String read = (String) unmarshaller.readObject();
        assertEquals(testObject, read);

        assertEquals(-1, unmarshaller.read());

        assertNull(ch.readOutbound());

        unmarshaller.finish();
        unmarshaller.close();
        buffer.release();
    }

    protected ByteBuf truncate(ByteBuf buf) {
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
