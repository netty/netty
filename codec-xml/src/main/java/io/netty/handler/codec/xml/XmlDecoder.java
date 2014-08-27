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

import com.fasterxml.aalto.AsyncInputFeeder;
import com.fasterxml.aalto.AsyncXMLStreamReader;
import com.fasterxml.aalto.stax.InputFactoryImpl;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import javax.xml.stream.events.XMLEvent;
import java.util.List;

/**
 * Async XML decoder based on Aalto XML parser.
 *
 * Parses the incoming data into one of XML messages defined in this package.
 */

public class XmlDecoder extends ByteToMessageDecoder {

    private static final InputFactoryImpl xmlInputFactory = new InputFactoryImpl();

    private final AsyncXMLStreamReader streamReader;
    private final AsyncInputFeeder streamFeeder;

    public XmlDecoder() {
        this.streamReader = xmlInputFactory.createAsyncXMLStreamReader();
        this.streamFeeder = streamReader.getInputFeeder();
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        byte[] buffer = new byte[in.readableBytes()];
        in.readBytes(buffer);
        streamFeeder.feedInput(buffer, 0, buffer.length);

        while(!streamFeeder.needMoreInput()){
            int type = streamReader.next();
            switch (type) {
                case XMLEvent.START_DOCUMENT:
                    out.add(new XmlDocumentStart(streamReader.getEncoding(), streamReader.getVersion(), streamReader.isStandalone(), streamReader.getCharacterEncodingScheme()));
                    break;
                case XMLEvent.END_DOCUMENT:
                    out.add(new XmlDocumentEnd());
                    break;
                case XMLEvent.START_ELEMENT:
                    XmlElementStart elementStart = new XmlElementStart(streamReader.getLocalName(), streamReader.getName().getNamespaceURI(), streamReader.getPrefix());
                    for(int x = 0; x < streamReader.getAttributeCount(); x++) {
                        XmlAttribute attribute = new XmlAttribute(streamReader.getAttributeType(x), streamReader.getAttributeLocalName(x), streamReader.getAttributePrefix(x), streamReader.getAttributeNamespace(x), streamReader.getAttributeValue(x));
                        elementStart.attributes().add(attribute);
                    }
                    for(int x = 0; x < streamReader.getNamespaceCount(); x++) {
                        XmlNamespace namespace = new XmlNamespace(streamReader.getNamespacePrefix(x), streamReader.getNamespaceURI(x));
                        elementStart.namespaces().add(namespace);
                    }
                    out.add(elementStart);
                    break;
                case XMLEvent.END_ELEMENT:
                    XmlElementEnd elementEnd = new XmlElementEnd(streamReader.getLocalName(), streamReader.getName().getNamespaceURI(), streamReader.getPrefix());
                    for(int x = 0; x < streamReader.getNamespaceCount(); x++) {
                        XmlNamespace namespace = new XmlNamespace(streamReader.getNamespacePrefix(x), streamReader.getNamespaceURI(x));
                        elementEnd.namespaces().add(namespace);
                    }
                    out.add(elementEnd);
                    break;
                case XMLEvent.PROCESSING_INSTRUCTION:
                    out.add(new XmlProcessingInstruction(streamReader.getPIData(), streamReader.getPITarget()));
                    break;
                case XMLEvent.CHARACTERS:
                    out.add(new XmlCharacters(streamReader.getText()));
                    break;
                case XMLEvent.COMMENT:
                    out.add(new XmlComment(streamReader.getText()));
                    break;
                case XMLEvent.SPACE:
                    out.add(new XmlSpace(streamReader.getText()));
                    break;
                case XMLEvent.ENTITY_REFERENCE:
                    out.add(new XmlEntityReference(streamReader.getLocalName(), streamReader.getText()));
                    break;
                case XMLEvent.DTD:
                    out.add(new XmlDTD(streamReader.getText()));
                    break;
                case XMLEvent.CDATA:
                    out.add(new XmlCdata(streamReader.getText()));
                    break;
            }
        }
    }

}
