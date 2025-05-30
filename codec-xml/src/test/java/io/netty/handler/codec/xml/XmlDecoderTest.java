/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.xml;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the basic functionality of the {@link XmlDecoder}.
 * XML borrowed from
 * <a href="https://www.studytrails.com/java/xml/woodstox/java-xml-woodstox-validation-xml-schema.jsp">
 * Woodstox : Validate against XML Schema</a>
 */
public class XmlDecoderTest {

    private static final String XML1 = "<?xml version=\"1.0\"?>" +
            "<!DOCTYPE employee SYSTEM \"employee.dtd\">" +
            "<?xml-stylesheet type=\"text/css\" href=\"netty.css\"?>" +
            "<?xml-test ?>" +
            "<employee xmlns:nettya=\"https://netty.io/netty/a\">" +
            "<nettya:id>&plusmn;1</nettya:id>\n" +
            "<name ";

    private static final String XML2 = "type=\"given\">Alba</name><![CDATA[ <some data &gt;/> ]]>" +
            "   <!-- namespaced --><nettyb:salary xmlns:nettyb=\"https://netty.io/netty/b\" nettyb:period=\"weekly\">" +
            "100</nettyb:salary><last/></employee>";

    private static final String XML3 = "<?xml version=\"1.1\" encoding=\"UTf-8\" standalone=\"yes\"?><netty></netty>";

    private static final String XML4 = "<netty></netty>";

    private EmbeddedChannel channel;

    @BeforeEach
    public void setup() throws Exception {
        channel = new EmbeddedChannel(new XmlDecoder());
    }

    @AfterEach
    public void teardown() throws Exception {
        channel.finish();
    }

    /**
     * This test feeds basic XML and verifies the resulting messages
     */
    @Test
    public void shouldDecodeRequestWithSimpleXml() {
        Object temp;

        write(XML1);

        temp = channel.readInbound();
        assertInstanceOf(XmlDocumentStart.class, temp);
        assertEquals("1.0", ((XmlDocumentStart) temp).version());
        assertEquals("UTF-8", ((XmlDocumentStart) temp).encoding());
        assertFalse(((XmlDocumentStart) temp).standalone());
        assertNull(((XmlDocumentStart) temp).encodingScheme());

        temp = channel.readInbound();
        assertInstanceOf(XmlDTD.class, temp);
        assertEquals("employee.dtd", ((XmlDTD) temp).text());

        temp = channel.readInbound();
        assertInstanceOf(XmlProcessingInstruction.class, temp);
        assertEquals("xml-stylesheet", ((XmlProcessingInstruction) temp).target());
        assertEquals("type=\"text/css\" href=\"netty.css\"", ((XmlProcessingInstruction) temp).data());

        temp = channel.readInbound();
        assertInstanceOf(XmlProcessingInstruction.class, temp);
        assertEquals("xml-test", ((XmlProcessingInstruction) temp).target());
        assertEquals("", ((XmlProcessingInstruction) temp).data());

        temp = channel.readInbound();
        assertInstanceOf(XmlElementStart.class, temp);
        assertEquals("employee", ((XmlElementStart) temp).name());
        assertEquals("", ((XmlElementStart) temp).prefix());
        assertEquals("", ((XmlElementStart) temp).namespace());
        assertEquals(0, ((XmlElementStart) temp).attributes().size());
        assertEquals(1, ((XmlElementStart) temp).namespaces().size());
        assertEquals("nettya", ((XmlElementStart) temp).namespaces().get(0).prefix());
        assertEquals("https://netty.io/netty/a", ((XmlElementStart) temp).namespaces().get(0).uri());

        temp = channel.readInbound();
        assertInstanceOf(XmlElementStart.class, temp);
        assertEquals("id", ((XmlElementStart) temp).name());
        assertEquals("nettya", ((XmlElementStart) temp).prefix());
        assertEquals("https://netty.io/netty/a", ((XmlElementStart) temp).namespace());
        assertEquals(0, ((XmlElementStart) temp).attributes().size());
        assertEquals(0, ((XmlElementStart) temp).namespaces().size());

        temp = channel.readInbound();
        assertInstanceOf(XmlEntityReference.class, temp);
        assertEquals("plusmn", ((XmlEntityReference) temp).name());
        assertEquals("", ((XmlEntityReference) temp).text());

        temp = channel.readInbound();
        assertInstanceOf(XmlCharacters.class, temp);
        assertEquals("1", ((XmlCharacters) temp).data());

        temp = channel.readInbound();
        assertInstanceOf(XmlElementEnd.class, temp);
        assertEquals("id", ((XmlElementEnd) temp).name());
        assertEquals("nettya", ((XmlElementEnd) temp).prefix());
        assertEquals("https://netty.io/netty/a", ((XmlElementEnd) temp).namespace());

        temp = channel.readInbound();
        assertInstanceOf(XmlCharacters.class, temp);
        assertEquals("\n", ((XmlCharacters) temp).data());

        temp = channel.readInbound();
        assertNull(temp);

        write(XML2);

        temp = channel.readInbound();
        assertInstanceOf(XmlElementStart.class, temp);
        assertEquals("name", ((XmlElementStart) temp).name());
        assertEquals("", ((XmlElementStart) temp).prefix());
        assertEquals("", ((XmlElementStart) temp).namespace());
        assertEquals(1, ((XmlElementStart) temp).attributes().size());
        assertEquals("type", ((XmlElementStart) temp).attributes().get(0).name());
        assertEquals("given", ((XmlElementStart) temp).attributes().get(0).value());
        assertEquals("", ((XmlElementStart) temp).attributes().get(0).prefix());
        assertEquals("", ((XmlElementStart) temp).attributes().get(0).namespace());
        assertEquals(0, ((XmlElementStart) temp).namespaces().size());

        temp = channel.readInbound();
        assertInstanceOf(XmlCharacters.class, temp);
        assertEquals("Alba", ((XmlCharacters) temp).data());

        temp = channel.readInbound();
        assertInstanceOf(XmlElementEnd.class, temp);
        assertEquals("name", ((XmlElementEnd) temp).name());
        assertEquals("", ((XmlElementEnd) temp).prefix());
        assertEquals("", ((XmlElementEnd) temp).namespace());

        temp = channel.readInbound();
        assertInstanceOf(XmlCdata.class, temp);
        assertEquals(" <some data &gt;/> ", ((XmlCdata) temp).data());

        temp = channel.readInbound();
        assertInstanceOf(XmlCharacters.class, temp);
        assertEquals("   ", ((XmlCharacters) temp).data());

        temp = channel.readInbound();
        assertInstanceOf(XmlComment.class, temp);
        assertEquals(" namespaced ", ((XmlComment) temp).data());

        temp = channel.readInbound();
        assertInstanceOf(XmlElementStart.class, temp);
        assertEquals("salary", ((XmlElementStart) temp).name());
        assertEquals("nettyb", ((XmlElementStart) temp).prefix());
        assertEquals("https://netty.io/netty/b", ((XmlElementStart) temp).namespace());
        assertEquals(1, ((XmlElementStart) temp).attributes().size());
        assertEquals("period", ((XmlElementStart) temp).attributes().get(0).name());
        assertEquals("weekly", ((XmlElementStart) temp).attributes().get(0).value());
        assertEquals("nettyb", ((XmlElementStart) temp).attributes().get(0).prefix());
        assertEquals("https://netty.io/netty/b", ((XmlElementStart) temp).attributes().get(0).namespace());
        assertEquals(1, ((XmlElementStart) temp).namespaces().size());
        assertEquals("nettyb", ((XmlElementStart) temp).namespaces().get(0).prefix());
        assertEquals("https://netty.io/netty/b", ((XmlElementStart) temp).namespaces().get(0).uri());

        temp = channel.readInbound();
        assertInstanceOf(XmlCharacters.class, temp);
        assertEquals("100", ((XmlCharacters) temp).data());

        temp = channel.readInbound();
        assertInstanceOf(XmlElementEnd.class, temp);
        assertEquals("salary", ((XmlElementEnd) temp).name());
        assertEquals("nettyb", ((XmlElementEnd) temp).prefix());
        assertEquals("https://netty.io/netty/b", ((XmlElementEnd) temp).namespace());
        assertEquals(1, ((XmlElementEnd) temp).namespaces().size());
        assertEquals("nettyb", ((XmlElementEnd) temp).namespaces().get(0).prefix());
        assertEquals("https://netty.io/netty/b", ((XmlElementEnd) temp).namespaces().get(0).uri());

        temp = channel.readInbound();
        assertInstanceOf(XmlElementStart.class, temp);
        assertEquals("last", ((XmlElementStart) temp).name());
        assertEquals("", ((XmlElementStart) temp).prefix());
        assertEquals("", ((XmlElementStart) temp).namespace());
        assertEquals(0, ((XmlElementStart) temp).attributes().size());
        assertEquals(0, ((XmlElementStart) temp).namespaces().size());

        temp = channel.readInbound();
        assertInstanceOf(XmlElementEnd.class, temp);
        assertEquals("last", ((XmlElementEnd) temp).name());
        assertEquals("", ((XmlElementEnd) temp).prefix());
        assertEquals("", ((XmlElementEnd) temp).namespace());
        assertEquals(0, ((XmlElementEnd) temp).namespaces().size());

        temp = channel.readInbound();
        assertInstanceOf(XmlElementEnd.class, temp);
        assertEquals("employee", ((XmlElementEnd) temp).name());
        assertEquals("", ((XmlElementEnd) temp).prefix());
        assertEquals("", ((XmlElementEnd) temp).namespace());
        assertEquals(1, ((XmlElementEnd) temp).namespaces().size());
        assertEquals("nettya", ((XmlElementEnd) temp).namespaces().get(0).prefix());
        assertEquals("https://netty.io/netty/a", ((XmlElementEnd) temp).namespaces().get(0).uri());

        temp = channel.readInbound();
        assertNull(temp);
    }

    /**
     * This test checks for different attributes in XML header
     */
    @Test
    public void shouldDecodeXmlHeader() {
        Object temp;

        write(XML3);

        temp = channel.readInbound();
        assertInstanceOf(XmlDocumentStart.class, temp);
        assertEquals("1.1", ((XmlDocumentStart) temp).version());
        assertEquals("UTF-8", ((XmlDocumentStart) temp).encoding());
        assertTrue(((XmlDocumentStart) temp).standalone());
        assertEquals("UTF-8", ((XmlDocumentStart) temp).encodingScheme());

        temp = channel.readInbound();
        assertInstanceOf(XmlElementStart.class, temp);
        assertEquals("netty", ((XmlElementStart) temp).name());
        assertEquals("", ((XmlElementStart) temp).prefix());
        assertEquals("", ((XmlElementStart) temp).namespace());
        assertEquals(0, ((XmlElementStart) temp).attributes().size());
        assertEquals(0, ((XmlElementStart) temp).namespaces().size());

        temp = channel.readInbound();
        assertInstanceOf(XmlElementEnd.class, temp);
        assertEquals("netty", ((XmlElementEnd) temp).name());
        assertEquals("", ((XmlElementEnd) temp).prefix());
        assertEquals("", ((XmlElementEnd) temp).namespace());
        assertEquals(0, ((XmlElementEnd) temp).namespaces().size());

        temp = channel.readInbound();
        assertNull(temp);
    }

    /**
     * This test checks for no XML header
     */
    @Test
    public void shouldDecodeWithoutHeader() {
        Object temp;

        write(XML4);

        temp = channel.readInbound();
        assertInstanceOf(XmlDocumentStart.class, temp);
        assertNull(((XmlDocumentStart) temp).version());
        assertEquals("UTF-8", ((XmlDocumentStart) temp).encoding());
        assertFalse(((XmlDocumentStart) temp).standalone());
        assertNull(((XmlDocumentStart) temp).encodingScheme());

        temp = channel.readInbound();
        assertInstanceOf(XmlElementStart.class, temp);
        assertEquals("netty", ((XmlElementStart) temp).name());
        assertEquals("", ((XmlElementStart) temp).prefix());
        assertEquals("", ((XmlElementStart) temp).namespace());
        assertEquals(0, ((XmlElementStart) temp).attributes().size());
        assertEquals(0, ((XmlElementStart) temp).namespaces().size());

        temp = channel.readInbound();
        assertInstanceOf(XmlElementEnd.class, temp);
        assertEquals("netty", ((XmlElementEnd) temp).name());
        assertEquals("", ((XmlElementEnd) temp).prefix());
        assertEquals("", ((XmlElementEnd) temp).namespace());
        assertEquals(0, ((XmlElementEnd) temp).namespaces().size());

        temp = channel.readInbound();
        assertNull(temp);
    }

    private void write(String content) {
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(content, CharsetUtil.UTF_8)));
    }
}
