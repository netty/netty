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

/**
 * Beginning of the XML document ... i.e. XML header
 */
public class XmlDocumentStart {

    private final String encoding;
    private final String version;
    private final boolean standalone;
    private final String encodingScheme;

    public XmlDocumentStart(String encoding, String version, boolean standalone, String encodingScheme) {
        this.encoding = encoding;
        this.version = version;
        this.standalone = standalone;
        this.encodingScheme = encodingScheme;
    }

    public String getEncoding() {
        return encoding;
    }

    public String getVersion() {
        return version;
    }

    public boolean isStandalone() {
        return standalone;
    }

    public String getEncodingScheme() {
        return encodingScheme;
    }
}
