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
 * Specific {@link io.netty.handler.codec.xml.XmlElement} representing beginning  of element,
 * with namespaces can also embed attributes.
 */
public class XmlElementStart extends XmlElement {

    private List<XmlAttribute> attributes = new LinkedList<XmlAttribute>();

    public XmlElementStart(String name, String namespace, String prefix) {
        super(name, namespace, prefix);
    }

    public List<XmlAttribute> getAttributes() {
        return attributes;
    }

}
