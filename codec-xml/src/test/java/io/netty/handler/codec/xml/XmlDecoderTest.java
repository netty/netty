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
 * Verifies the basic functionality of the {@link io.netty.handler.codec.xml.XmlDecoder}.
 */
public class XmlDecoderTest {

    private static final String XML1 = "<?xml version=\"1.0\"?><employee>\n" +
            "<id>1</id>\n" +
            "<name ";

    private static final String XML2 = "type=\"given\">Alba</name>" +
            "    <salary>100</salary>\n" +
            "</employee>";

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

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlElementStart.class));
        assertThat(((XmlElementStart) temp).name(), is("employee"));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlCharacters.class));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlElementStart.class));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlCharacters.class));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlElementEnd.class));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlCharacters.class));

        temp = channel.readInbound();
        assertThat(temp, nullValue());

        write(XML2);

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlElementStart.class));
        assertThat(((XmlElementStart) temp).attributes().size(), is(1));
        assertThat(((XmlElementStart) temp).attributes().get(0).name(), is("type"));
        assertThat(((XmlElementStart) temp).attributes().get(0).value(), is("given"));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlCharacters.class));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlElementEnd.class));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlCharacters.class));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlElementStart.class));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlCharacters.class));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlElementEnd.class));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlCharacters.class));

        temp = channel.readInbound();
        assertThat(temp, instanceOf(XmlElementEnd.class));

        temp = channel.readInbound();
        assertThat(temp, nullValue());
    }

    private void write(String content) {
        assertThat(channel.writeInbound(Unpooled.copiedBuffer(content, CharsetUtil.UTF_8)), is(true));
    }

}
