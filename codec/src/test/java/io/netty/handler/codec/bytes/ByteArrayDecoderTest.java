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

import static org.hamcrest.core.Is.is;
import static io.netty.buffer.ChannelBuffers.wrappedBuffer;
import static org.junit.Assert.assertThat;

import java.util.Random;

import io.netty.handler.codec.embedder.DecoderEmbedder;
import org.junit.Before;
import org.junit.Test;

/**
 */
public class ByteArrayDecoderTest {

    private DecoderEmbedder<byte[]> embedder;

    @Before
    public void setUp() {
        embedder = new DecoderEmbedder<byte[]>(new ByteArrayDecoder());
    }
	
	@Test
	public void testDecode() {
        byte[] b = new byte[2048];
        new Random().nextBytes(b);
        embedder.offer(wrappedBuffer(b));
        assertThat(embedder.poll(), is(b));
	}
	
	@Test
	public void testDecodeEmpty() {
        byte[] b = new byte[0];
        embedder.offer(wrappedBuffer(b));
        assertThat(embedder.poll(), is(b));
	}
	
	@Test
	public void testDecodeOtherType() {
        String str = "Meep!";
        embedder.offer(str);
        assertThat(embedder.poll(), is((Object) str));
	}

}
