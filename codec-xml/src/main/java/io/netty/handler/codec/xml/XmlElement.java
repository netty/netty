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

import java.util.LinkedList;
import java.util.List;

/**
 * Generic XML element in document, embeds only Namespaces {@link io.netty.handler.codec.xml.XmlNamespace} defined
 * on the element, as those are common to start and end elements.
 */
public class XmlElement {

    private String name;
    private String namespace;
    private String prefix;

    private List<XmlNamespace> namespaces = new LinkedList<XmlNamespace>();

    public XmlElement(String name, String namespace, String prefix) {
        this.name = name;
        this.namespace = namespace;
        this.prefix = prefix;
    }

    public String getName() {
        return name;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getPrefix() {
        return prefix;
    }

    public List<XmlNamespace> getNamespaces() {
        return namespaces;
    }

}
