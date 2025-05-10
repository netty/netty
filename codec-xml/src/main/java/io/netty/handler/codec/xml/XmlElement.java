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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Generic XML element in document, {@link XmlElementStart} represents open element and provides access to attributes,
 * {@link XmlElementEnd} represents closing element.
 */
public abstract class XmlElement {

    private final String name;
    private final String namespace;
    private final String prefix;

    private final List<XmlNamespace> namespaces = new ArrayList<>();

    protected XmlElement(String name, String namespace, String prefix) {
        this.name = name;
        this.namespace = namespace;
        this.prefix = prefix;
    }

    public String name() {
        return name;
    }

    public String namespace() {
        return namespace;
    }

    public String prefix() {
        return prefix;
    }

    public List<XmlNamespace> namespaces() {
        return namespaces;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        XmlElement that = (XmlElement) o;

        return Objects.equals(name, that.name) &&
                Objects.equals(namespace, that.namespace) &&
                Objects.equals(namespaces, that.namespaces) &&
                Objects.equals(prefix, that.prefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, namespace, prefix, namespaces);
    }

    @Override
    public String toString() {
        return ", name='" + name + '\'' +
                ", namespace='" + namespace + '\'' +
                ", prefix='" + prefix + '\'' +
                ", namespaces=" + namespaces;
    }

}
