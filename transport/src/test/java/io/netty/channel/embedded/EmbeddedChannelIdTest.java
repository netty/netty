/*
 * Copyright 2015 The Netty Project
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
package io.netty.channel.embedded;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelId;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class EmbeddedChannelIdTest {

    @Test
    public void testSerialization() throws IOException, ClassNotFoundException {
        // test that a deserialized instance works the same as a normal instance (issue #2869)
        ChannelId normalInstance = EmbeddedChannelId.INSTANCE;

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

        Assert.assertEquals(normalInstance, deserializedInstance);
        Assert.assertEquals(normalInstance.hashCode(), deserializedInstance.hashCode());
        Assert.assertEquals(0, normalInstance.compareTo(deserializedInstance));
    }

}
