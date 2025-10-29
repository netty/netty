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

import static io.netty.util.internal.ObjectUtil.hash;

/**
 * XML entity reference ... {@code &#nnnn;}
 */
public class XmlEntityReference {

    private final String name;
    private final String text;

    public XmlEntityReference(String name, String text) {
        this.name = name;
        this.text = text;
    }

    public String name() {
        return name;
    }

    public String text() {
        return text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        XmlEntityReference that = (XmlEntityReference) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return hash(name, text);
    }

    @Override
    public String toString() {
        return "XmlEntityReference{" +
                "name='" + name + '\'' +
                ", text='" + text + '\'' +
                '}';
    }
}
