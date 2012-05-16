/*
 * Copyright 2011 The Netty Project
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
package io.netty.handler.codec.bytes;

import static io.netty.buffer.ChannelBuffers.*;
import static org.hamcrest.core.Is.*;
import static org.hamcrest.core.IsNull.*;
import static org.junit.Assert.*;
import io.netty.buffer.ChannelBuffer;
import io.netty.handler.codec.embedder.EncoderEmbedder;

import java.util.Random;

import org.junit.Before;
import org.junit.Test;

/**
 */
public class ByteArrayEncoderTest {

    private EncoderEmbedder<ChannelBuffer> embedder;

    @Before
    public void setUp() {
        embedder = new EncoderEmbedder<ChannelBuffer>(new ByteArrayEncoder());
    }

    @Test
    public void testEncode() {
        byte[] b = new byte[2048];
        new Random().nextBytes(b);
        embedder.offer(b);
        assertThat(embedder.poll(), is(wrappedBuffer(b)));
    }

    @Test
    public void testEncodeEmpty() {
        byte[] b = new byte[0];
        embedder.offer(b);
        assertThat(embedder.poll(), nullValue());
    }

    @Test
    public void testEncodeOtherType() {
        String str = "Meep!";
        embedder.offer(str);
        try {
            embedder.poll();
            fail();
        } catch (ClassCastException e) {
            // Expected
        }
    }

}
