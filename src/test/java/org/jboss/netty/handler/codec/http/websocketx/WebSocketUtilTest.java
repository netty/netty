/*
 * Copyright 2013 The Netty Project
 * 
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.jboss.netty.handler.codec.http.websocketx;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

import org.junit.Test;

import java.nio.charset.Charset;

import static org.junit.Assert.*;

public class WebSocketUtilTest {

    private final static Charset UTF_8 = Charset.forName("UTF-8");

    @Test
    public void testMd5() {
        byte[] bytes1 = "hello, world".getBytes(UTF_8);
        ChannelBuffer buf1 = ChannelBuffers.wrappedBuffer(bytes1, 0, bytes1.length);
        byte[] bytes2 = "   hello, world".getBytes(UTF_8);
        ChannelBuffer buf2 = ChannelBuffers.wrappedBuffer(bytes2, 3, bytes2.length - 3);

        assertEquals(buf1, buf2);

        ChannelBuffer digest1 = WebSocketUtil.md5(buf1);
        ChannelBuffer digest2 = WebSocketUtil.md5(buf2);

        assertEquals(digest1, digest2);
    }
    
    @Test
    public void testSha1() {
        byte[] bytes1 = "hello, world".getBytes(UTF_8);
        ChannelBuffer buf1 = ChannelBuffers.wrappedBuffer(bytes1, 0, bytes1.length);
        byte[] bytes2 = "   hello, world".getBytes(UTF_8);
        ChannelBuffer buf2 = ChannelBuffers.wrappedBuffer(bytes2, 3, bytes2.length - 3);

        assertEquals(buf1, buf2);

        ChannelBuffer digest1 = WebSocketUtil.sha1(buf1);
        ChannelBuffer digest2 = WebSocketUtil.sha1(buf2);

        assertEquals(digest1, digest2);
    }

}
