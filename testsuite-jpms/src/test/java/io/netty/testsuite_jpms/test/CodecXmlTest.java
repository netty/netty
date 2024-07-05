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

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.xml.XmlDecoder;
import io.netty.handler.codec.xml.XmlDocumentStart;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CodecXmlTest {

    private static final String XML1 = "<?xml version=\"1.0\"?>" +
            "<!DOCTYPE employee SYSTEM \"employee.dtd\">" +
            "<?xml-stylesheet type=\"text/css\" href=\"netty.css\"?>" +
            "<?xml-test ?>" +
            "<employee xmlns:nettya=\"https://netty.io/netty/a\">" +
            "<nettya:id>&plusmn;1</nettya:id>\n" +
            "<name ";

    @Test
    public void testDecoder() {
        EmbeddedChannel channel = new EmbeddedChannel(new XmlDecoder());
        try {
            assertTrue(channel.writeInbound(Unpooled.copiedBuffer(XML1, CharsetUtil.UTF_8)));
            Object msg = channel.readInbound();
            assertInstanceOf(XmlDocumentStart.class, msg);
        } finally {
            channel.close();
        }
    }
}
