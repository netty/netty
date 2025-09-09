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


import java.util.Objects;

/**
 * XML attributes, it is part of {@link XmlElement}
 */
public class XmlAttribute {

    private final String type;
    private final String name;
    private final String prefix;
    private final String namespace;
    private final String value;

    public XmlAttribute(String type, String name, String prefix, String namespace, String value) {
        this.type = type;
        this.name = name;
        this.prefix = prefix;
        this.namespace = namespace;
        this.value = value;
    }

    public String type() {
        return type;
    }

    public String name() {
        return name;
    }

    public String prefix() {
        return prefix;
    }

    public String namespace() {
        return namespace;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        XmlAttribute that = (XmlAttribute) o;
        return name.equals(that.name) &&
                Objects.equals(namespace, that.namespace) &&
                Objects.equals(prefix, that.prefix) &&
                Objects.equals(type, that.type) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name, prefix, namespace, value);
    }

    @Override
    public String toString() {
        return "XmlAttribute{" +
                "type='" + type + '\'' +
                ", name='" + name + '\'' +
                ", prefix='" + prefix + '\'' +
                ", namespace='" + namespace + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
