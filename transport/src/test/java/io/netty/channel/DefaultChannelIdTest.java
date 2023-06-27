/*
 * Copyright 2013 The Netty Project
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

package io.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("DynamicRegexReplaceableByCompiledPattern")
public class DefaultChannelIdTest {
    @Test
    public void testShortText() {
        String text = DefaultChannelId.newInstance().asShortText();
        assertTrue(text.matches("^[0-9a-f]{8}$"));
    }

    @Test
    public void testLongText() {
        String text = DefaultChannelId.newInstance().asLongText();
        assertTrue(text.matches("^[0-9a-f]{16}-[0-9a-f]{8}-[0-9a-f]{8}-[0-9a-f]{16}-[0-9a-f]{8}$"));
    }

    @Test
    public void testIdempotentMachineId() {
        String a = DefaultChannelId.newInstance().asLongText().substring(0, 16);
        String b = DefaultChannelId.newInstance().asLongText().substring(0, 16);
        assertThat(a, is(b));
    }

    @Test
    public void testIdempotentProcessId() {
        String a = DefaultChannelId.newInstance().asLongText().substring(17, 21);
        String b = DefaultChannelId.newInstance().asLongText().substring(17, 21);
        assertThat(a, is(b));
    }

    @Test
    public void testSerialization() throws Exception {
        ChannelId a = DefaultChannelId.newInstance();
        ChannelId b;

        ByteBuf buf = Unpooled.buffer();
        ObjectOutputStream out = new ObjectOutputStream(new ByteBufOutputStream(buf));
        try {
            out.writeObject(a);
            out.flush();
        } finally {
            out.close();
        }

        ObjectInputStream in = new ObjectInputStream(new ByteBufInputStream(buf, true));
        try {
            b = (ChannelId) in.readObject();
        } finally {
            in.close();
        }

        assertThat(a, is(b));
        assertThat(a, is(not(sameInstance(b))));
        assertThat(a.asLongText(), is(b.asLongText()));
    }
}
