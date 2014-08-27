/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.xml;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.core.IsNull.nullValue;

/**
 * Verifies the basic functionality of the {@link XmlDecoder}.
 * XML borrowed from <a href="http://www.studytrails.com/java/xml/woodstox/java-xml-woodstox-validation-xml-schema.jsp">
 * Woodstox : Validate against XML Schema</a>
 */
public class XmlDecoderTest {

    private static final String XML1 = "<?xml version=\"1.0\"?>" +
            "<!DOCTYPE employee SYSTEM \"employee.dtd\">" +
            "<?xml-stylesheet type=\"text/css\" href=\"netty.css\"?>" +
            "<?xml-test ?>" +
            "<employee xmlns:nettya=\"http://netty.io/netty/a\">" +
            "<nettya:id>&plusmn;1</nettya:id>\n" +
            "<name ";

    private static final String XML2 = "type=\"given\">Alba</name><![CDATA[ <some data &gt;/> ]]>" +
            "   <!-- namespaced --><nettyb:salary xmlns:nettyb=\"http://netty.io/netty/b\" nettyb:period=\"weekly\">" +
            "100</nettyb:salary><last/></employee>";

    private static final String XML3 = "<?xml version=\"1.1\" encoding=\"UTf-8\" standalone=\"yes\"?><netty></netty>";

    private static final String XML4 = "<netty></netty>";

    private EmbeddedChannel channel;

    @Before
    public void setup() throws Exception {
        channel = new EmbeddedChannel(new XmlDecoder());
    }

    @After
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
        assertThat(temp, instanceOf(XmlDocumentStart.class));
        assertThat(((XmlDocumentStart) temp).version(), is("1.0"));
        assertThat(((XmlDocumentStart) temp).encoding(), is("UTF-8"));
        assertThat(((XmlDocumentStart) temp).standalone(), is(false));
        assertThat(((XmlDocumentStart) temp).encodingScheme(), is(nullValue()));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlDTD.class));
        assertThat(((XmlDTD) temp).text(), is("employee.dtd"));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlProcessingInstruction.class));
        assertThat(((XmlProcessingInstruction) temp).target(), is("xml-stylesheet"));
        assertThat(((XmlProcessingInstruction) temp).data(), is("type=\"text/css\" href=\"netty.css\""));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlProcessingInstruction.class));
        assertThat(((XmlProcessingInstruction) temp).target(), is("xml-test"));
        assertThat(((XmlProcessingInstruction) temp).data(), is(""));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlElementStart.class));
        assertThat(((XmlElementStart) temp).name(), is("employee"));
        assertThat(((XmlElementStart) temp).prefix(), is(""));
        assertThat(((XmlElementStart) temp).namespace(), is(""));
        assertThat(((XmlElementStart) temp).attributes().size(), is(0));
        assertThat(((XmlElementStart) temp).namespaces().size(), is(1));
        assertThat(((XmlElementStart) temp).namespaces().get(0).prefix(), is("nettya"));
        assertThat(((XmlElementStart) temp).namespaces().get(0).uri(), is("http://netty.io/netty/a"));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlElementStart.class));
        assertThat(((XmlElementStart) temp).name(), is("id"));
        assertThat(((XmlElementStart) temp).prefix(), is("nettya"));
        assertThat(((XmlElementStart) temp).namespace(), is("http://netty.io/netty/a"));
        assertThat(((XmlElementStart) temp).attributes().size(), is(0));
        assertThat(((XmlElementStart) temp).namespaces().size(), is(0));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlEntityReference.class));
        assertThat(((XmlEntityReference) temp).name(), is("plusmn"));
        assertThat(((XmlEntityReference) temp).text(), is(""));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlCharacters.class));
        assertThat(((XmlCharacters) temp).data(), is("1"));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlElementEnd.class));
        assertThat(((XmlElementEnd) temp).name(), is("id"));
        assertThat(((XmlElementEnd) temp).prefix(), is("nettya"));
        assertThat(((XmlElementEnd) temp).namespace(), is("http://netty.io/netty/a"));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlCharacters.class));
        assertThat(((XmlCharacters) temp).data(), is("\n"));

        temp = channel.readInbound();
        assertThat(temp, nullValue());

        write(XML2);

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlElementStart.class));
        assertThat(((XmlElementStart) temp).name(), is("name"));
        assertThat(((XmlElementStart) temp).prefix(), is(""));
        assertThat(((XmlElementStart) temp).namespace(), is(""));
        assertThat(((XmlElementStart) temp).attributes().size(), is(1));
        assertThat(((XmlElementStart) temp).attributes().get(0).name(), is("type"));
        assertThat(((XmlElementStart) temp).attributes().get(0).value(), is("given"));
        assertThat(((XmlElementStart) temp).attributes().get(0).prefix(), is(""));
        assertThat(((XmlElementStart) temp).attributes().get(0).namespace(), is(""));
        assertThat(((XmlElementStart) temp).namespaces().size(), is(0));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlCharacters.class));
        assertThat(((XmlCharacters) temp).data(), is("Alba"));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlElementEnd.class));
        assertThat(((XmlElementEnd) temp).name(), is("name"));
        assertThat(((XmlElementEnd) temp).prefix(), is(""));
        assertThat(((XmlElementEnd) temp).namespace(), is(""));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlCdata.class));
        assertThat(((XmlCdata) temp).data(), is(" <some data &gt;/> "));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlCharacters.class));
        assertThat(((XmlCharacters) temp).data(), is("   "));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlComment.class));
        assertThat(((XmlComment) temp).data(), is(" namespaced "));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlElementStart.class));
        assertThat(((XmlElementStart) temp).name(), is("salary"));
        assertThat(((XmlElementStart) temp).prefix(), is("nettyb"));
        assertThat(((XmlElementStart) temp).namespace(), is("http://netty.io/netty/b"));
        assertThat(((XmlElementStart) temp).attributes().size(), is(1));
        assertThat(((XmlElementStart) temp).attributes().get(0).name(), is("period"));
        assertThat(((XmlElementStart) temp).attributes().get(0).value(), is("weekly"));
        assertThat(((XmlElementStart) temp).attributes().get(0).prefix(), is("nettyb"));
        assertThat(((XmlElementStart) temp).attributes().get(0).namespace(), is("http://netty.io/netty/b"));
        assertThat(((XmlElementStart) temp).namespaces().size(), is(1));
        assertThat(((XmlElementStart) temp).namespaces().get(0).prefix(), is("nettyb"));
        assertThat(((XmlElementStart) temp).namespaces().get(0).uri(), is("http://netty.io/netty/b"));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlCharacters.class));
        assertThat(((XmlCharacters) temp).data(), is("100"));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlElementEnd.class));
        assertThat(((XmlElementEnd) temp).name(), is("salary"));
        assertThat(((XmlElementEnd) temp).prefix(), is("nettyb"));
        assertThat(((XmlElementEnd) temp).namespace(), is("http://netty.io/netty/b"));
        assertThat(((XmlElementEnd) temp).namespaces().size(), is(1));
        assertThat(((XmlElementEnd) temp).namespaces().get(0).prefix(), is("nettyb"));
        assertThat(((XmlElementEnd) temp).namespaces().get(0).uri(), is("http://netty.io/netty/b"));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlElementStart.class));
        assertThat(((XmlElementStart) temp).name(), is("last"));
        assertThat(((XmlElementStart) temp).prefix(), is(""));
        assertThat(((XmlElementStart) temp).namespace(), is(""));
        assertThat(((XmlElementStart) temp).attributes().size(), is(0));
        assertThat(((XmlElementStart) temp).namespaces().size(), is(0));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlElementEnd.class));
        assertThat(((XmlElementEnd) temp).name(), is("last"));
        assertThat(((XmlElementEnd) temp).prefix(), is(""));
        assertThat(((XmlElementEnd) temp).namespace(), is(""));
        assertThat(((XmlElementEnd) temp).namespaces().size(), is(0));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlElementEnd.class));
        assertThat(((XmlElementEnd) temp).name(), is("employee"));
        assertThat(((XmlElementEnd) temp).prefix(), is(""));
        assertThat(((XmlElementEnd) temp).namespace(), is(""));
        assertThat(((XmlElementEnd) temp).namespaces().size(), is(1));
        assertThat(((XmlElementEnd) temp).namespaces().get(0).prefix(), is("nettya"));
        assertThat(((XmlElementEnd) temp).namespaces().get(0).uri(), is("http://netty.io/netty/a"));

        temp = channel.readInbound();
        assertThat(temp, nullValue());
    }

    /**
     * This test checks for different attributes in XML header
     */
    @Test
    public void shouldDecodeXmlHeader() {
        Object temp;

        write(XML3);

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlDocumentStart.class));
        assertThat(((XmlDocumentStart) temp).version(), is("1.1"));
        assertThat(((XmlDocumentStart) temp).encoding(), is("UTF-8"));
        assertThat(((XmlDocumentStart) temp).standalone(), is(true));
        assertThat(((XmlDocumentStart) temp).encodingScheme(), is("UTF-8"));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlElementStart.class));
        assertThat(((XmlElementStart) temp).name(), is("netty"));
        assertThat(((XmlElementStart) temp).prefix(), is(""));
        assertThat(((XmlElementStart) temp).namespace(), is(""));
        assertThat(((XmlElementStart) temp).attributes().size(), is(0));
        assertThat(((XmlElementStart) temp).namespaces().size(), is(0));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlElementEnd.class));
        assertThat(((XmlElementEnd) temp).name(), is("netty"));
        assertThat(((XmlElementEnd) temp).prefix(), is(""));
        assertThat(((XmlElementEnd) temp).namespace(), is(""));
        assertThat(((XmlElementEnd) temp).namespaces().size(), is(0));

        temp = channel.readInbound();
        assertThat(temp, nullValue());
    }

    /**
     * This test checks for no XML header
     */
    @Test
    public void shouldDecodeWithoutHeader() {
        Object temp;

        write(XML4);

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlDocumentStart.class));
        assertThat(((XmlDocumentStart) temp).version(), is(nullValue()));
        assertThat(((XmlDocumentStart) temp).encoding(), is("UTF-8"));
        assertThat(((XmlDocumentStart) temp).standalone(), is(false));
        assertThat(((XmlDocumentStart) temp).encodingScheme(), is(nullValue()));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlElementStart.class));
        assertThat(((XmlElementStart) temp).name(), is("netty"));
        assertThat(((XmlElementStart) temp).prefix(), is(""));
        assertThat(((XmlElementStart) temp).namespace(), is(""));
        assertThat(((XmlElementStart) temp).attributes().size(), is(0));
        assertThat(((XmlElementStart) temp).namespaces().size(), is(0));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlElementEnd.class));
        assertThat(((XmlElementEnd) temp).name(), is("netty"));
        assertThat(((XmlElementEnd) temp).prefix(), is(""));
        assertThat(((XmlElementEnd) temp).namespace(), is(""));
        assertThat(((XmlElementEnd) temp).namespaces().size(), is(0));

        temp = channel.readInbound();
        assertThat(temp, nullValue());
    }

    private void write(String content) {
        assertThat(channel.writeInbound(Unpooled.copiedBuffer(content, CharsetUtil.UTF_8)), is(true));
    }

}
