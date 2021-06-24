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
package io.netty.handler.codec.http2;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelId;
import io.netty.channel.DefaultChannelId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Http2StreamChannelIdTest {

    @Test
    public void testSerialization() throws Exception {
        ChannelId normalInstance = new Http2StreamChannelId(DefaultChannelId.newInstance(), 0);

        ByteBuf buf = Unpooled.buffer();
        ObjectOutputStream outStream = new ObjectOutputStream(new ByteBufOutputStream(buf));
        try {
            outStream.writeObject(normalInstance);
        } finally {
            outStream.close();
        }

        ObjectInputStream inStream = new ObjectInputStream(new ByteBufInputStream(buf, true));
        final ChannelId deserializedInstance;
        try {
            deserializedInstance = (ChannelId) inStream.readObject();
        } finally {
            inStream.close();
        }

        assertEquals(normalInstance, deserializedInstance);
        assertEquals(normalInstance.hashCode(), deserializedInstance.hashCode());
        assertEquals(0, normalInstance.compareTo(deserializedInstance));
        assertEquals(normalInstance.asLongText(), deserializedInstance.asLongText());
        assertEquals(normalInstance.asShortText(), deserializedInstance.asShortText());
    }
}
