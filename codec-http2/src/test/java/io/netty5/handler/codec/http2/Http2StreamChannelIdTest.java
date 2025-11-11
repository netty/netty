/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.handler.codec.http2;

import io.netty5.buffer.BufferInputStream;
import io.netty5.buffer.BufferOutputStream;
import io.netty5.buffer.Buffer;
import io.netty5.channel.ChannelId;
import io.netty5.channel.DefaultChannelId;
import org.junit.jupiter.api.Test;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static io.netty5.buffer.DefaultBufferAllocators.preferredAllocator;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class Http2StreamChannelIdTest {

    @Test
    public void testSerialization() throws Exception {
        ChannelId normalInstance = new Http2StreamChannelId(DefaultChannelId.newInstance(), 0);

        Buffer buf = preferredAllocator().allocate(256);
        try (ObjectOutputStream outStream = new ObjectOutputStream(new BufferOutputStream(buf))) {
            outStream.writeObject(normalInstance);
        }

        final ChannelId deserializedInstance;
        try (ObjectInputStream inStream = new ObjectInputStream(new BufferInputStream(buf))) {
            deserializedInstance = (ChannelId) inStream.readObject();
        }

        assertEquals(normalInstance, deserializedInstance);
        assertEquals(normalInstance.hashCode(), deserializedInstance.hashCode());
        assertEquals(0, normalInstance.compareTo(deserializedInstance));
        assertEquals(normalInstance.asLongText(), deserializedInstance.asLongText());
        assertEquals(normalInstance.asShortText(), deserializedInstance.asShortText());
    }
}
