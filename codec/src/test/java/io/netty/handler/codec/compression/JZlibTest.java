/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.After;
import org.junit.Test;
import io.netty.channel.embedded.EmbeddedByteChannel;

import static org.junit.Assert.*;

public class JZlibTest {

    @After
    public void resetSnappy() {
    }

    @Test
    public void testZLIB() throws Exception {
        ByteBuf data = Unpooled.wrappedBuffer("test".getBytes());

        EmbeddedByteChannel chEncoder =
          new EmbeddedByteChannel(new JZlibEncoder(ZlibWrapper.ZLIB));

        chEncoder.writeOutbound(data);
        assertTrue(chEncoder.finish());

        byte[] deflatedData = chEncoder.readOutbound().array();

        {
          EmbeddedByteChannel chDecoder =
            new EmbeddedByteChannel(new JZlibDecoder(ZlibWrapper.ZLIB));

          chDecoder.writeInbound(Unpooled.wrappedBuffer(deflatedData));
          assertTrue(chDecoder.finish());

          assertEquals(data, chDecoder.readInbound());
        }

        {
          EmbeddedByteChannel chDecoder =
            new EmbeddedByteChannel(new JZlibDecoder(ZlibWrapper.ZLIB_OR_NONE));

          chDecoder.writeInbound(Unpooled.wrappedBuffer(deflatedData));
          assertTrue(chDecoder.finish());

          assertEquals(data, chDecoder.readInbound());
        }
    }

    @Test
    public void testNONE() throws Exception {
        ByteBuf data = Unpooled.wrappedBuffer("test".getBytes());

        EmbeddedByteChannel chEncoder =
          new EmbeddedByteChannel(new JZlibEncoder(ZlibWrapper.NONE));

        chEncoder.writeOutbound(data);
        assertTrue(chEncoder.finish());
  
        byte[] deflatedData = chEncoder.readOutbound().array();

        {
          EmbeddedByteChannel chDecoder =
            new EmbeddedByteChannel(new JZlibDecoder(ZlibWrapper.NONE));

          chDecoder.writeInbound(Unpooled.wrappedBuffer(deflatedData));
          assertTrue(chDecoder.finish());

          assertEquals(data, chDecoder.readInbound());
        }

        {
          EmbeddedByteChannel chDecoder =
            new EmbeddedByteChannel(new JZlibDecoder(ZlibWrapper.ZLIB_OR_NONE));

          chDecoder.writeInbound(Unpooled.wrappedBuffer(deflatedData));
          assertTrue(chDecoder.finish());

          assertEquals(data, chDecoder.readInbound());
        }
    }


    @Test
    public void testGZIP() throws Exception {
        ByteBuf data = Unpooled.wrappedBuffer("test".getBytes());

        EmbeddedByteChannel chEncoder =
          new EmbeddedByteChannel(new JZlibEncoder(ZlibWrapper.GZIP));

        chEncoder.writeOutbound(data);
        assertTrue(chEncoder.finish());

        byte[] deflatedData = chEncoder.readOutbound().array();

        {
          EmbeddedByteChannel chDecoder =
            new EmbeddedByteChannel(new JZlibDecoder(ZlibWrapper.GZIP));

          chDecoder.writeInbound(Unpooled.wrappedBuffer(deflatedData));
          assertTrue(chDecoder.finish());

          assertEquals(data, chDecoder.readInbound());
        }


	// This case will be failed with netty's jzlib.
      {
          EmbeddedByteChannel chDecoder =
            new EmbeddedByteChannel(new JZlibDecoder(ZlibWrapper.ZLIB_OR_NONE));

          chDecoder.writeInbound(Unpooled.wrappedBuffer(deflatedData));
          assertTrue(chDecoder.finish());

          assertEquals(data, chDecoder.readInbound());
        }
    }
}
