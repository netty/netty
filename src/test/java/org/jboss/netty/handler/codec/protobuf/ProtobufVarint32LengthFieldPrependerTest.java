/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.protobuf;

import static org.hamcrest.core.Is.*;
import static org.jboss.netty.buffer.ChannelBuffers.*;
import static org.junit.Assert.*;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.embedder.EncoderEmbedder;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Tomasz Blachowicz (tblachowicz@gmail.com)
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class ProtobufVarint32LengthFieldPrependerTest {

    private EncoderEmbedder<ChannelBuffer> embedder;

    @Before
    public void setUp() {
        embedder = new EncoderEmbedder<ChannelBuffer>(
                new ProtobufVarint32LengthFieldPrepender());
    }

    @Test
    public void testTinyEncode() {
        byte[] b = new byte[] { 4, 1, 1, 1, 1 };
        embedder.offer(wrappedBuffer(b, 1, b.length - 1));
        assertThat(embedder.poll(), is(wrappedBuffer(b)));
    }

    @Test
    public void testRegularDecode() {
        byte[] b = new byte[2048];
        for (int i = 2; i < 2048; i ++) {
            b[i] = 1;
        }
        b[0] = -2;
        b[1] = 15;
        embedder.offer(wrappedBuffer(b, 2, b.length - 2));
        assertThat(embedder.poll(), is(wrappedBuffer(b)));
    }
}
