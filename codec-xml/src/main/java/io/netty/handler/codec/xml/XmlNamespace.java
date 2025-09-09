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
 * XML namespace is part of XML element.
 */
public class XmlNamespace {

    private final String prefix;
    private final String uri;

    public XmlNamespace(String prefix, String uri) {
        this.prefix = prefix;
        this.uri = uri;
    }

    public String prefix() {
        return prefix;
    }

    public String uri() {
        return uri;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        XmlNamespace that = (XmlNamespace) o;
        return Objects.equals(prefix, that.prefix) &&
                Objects.equals(uri, that.uri);
    }

    @Override
    public int hashCode() {
        return hash(prefix, uri);
    }

    @Override
    public String toString() {
        return "XmlNamespace{" +
                "prefix='" + prefix + '\'' +
                ", uri='" + uri + '\'' +
                '}';
    }

}
