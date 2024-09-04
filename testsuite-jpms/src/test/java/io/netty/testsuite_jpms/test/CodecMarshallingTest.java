/*
 * Copyright 2024 The Netty Project
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

package io.netty.testsuite_jpms.test;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.marshalling.CompatibleMarshallingEncoder;
import io.netty.handler.codec.marshalling.DefaultMarshallerProvider;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.junit.jupiter.api.Test;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static org.junit.jupiter.api.Assertions.*;

public class CodecMarshallingTest {

    @Test
    public void smokeTest() throws Exception {
        @SuppressWarnings("RedundantStringConstructorCall")
        String testObject = new String("test");
        MarshallingConfiguration configuration = new MarshallingConfiguration();
        configuration.setVersion(5);
        MarshallerFactory marshallerFactory = Marshalling.getProvidedMarshallerFactory("serial");
        DefaultMarshallerProvider provider = new DefaultMarshallerProvider(marshallerFactory, configuration);
        EmbeddedChannel ch = new EmbeddedChannel(new CompatibleMarshallingEncoder(provider));
        ch.writeOutbound(testObject);
        assertTrue(ch.finish());
        ByteBuf buffer = ch.readOutbound();
        Unmarshaller unmarshaller = marshallerFactory.createUnmarshaller(configuration);
        unmarshaller.start(Marshalling.createByteInput(buffer.nioBuffer()));
        String read = (String) unmarshaller.readObject();
        assertEquals(testObject, read);
        assertEquals(-1, unmarshaller.read());
        assertNull(ch.readOutbound());
        unmarshaller.finish();
        unmarshaller.close();
        buffer.release();
    }
}
