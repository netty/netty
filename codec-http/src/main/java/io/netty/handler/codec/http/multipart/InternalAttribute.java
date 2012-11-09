/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.http.multipart;

import java.util.ArrayList;
import java.util.List;

/**
 * This Attribute is only for Encoder use to insert special command between object if needed
 * (like Multipart Mixed mode)
 */
public class InternalAttribute implements InterfaceHttpData {
    protected List<String> value = new ArrayList<String>();

    @Override
    public HttpDataType getHttpDataType() {
        return HttpDataType.InternalAttribute;
    }

    public List<String> getValue() {
        return value;
    }

    public void addValue(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        this.value.add(value);
    }

    public void addValue(String value, int rank) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        this.value.add(rank, value);
    }

    public void setValue(String value, int rank) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        this.value.set(rank, value);
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Attribute)) {
            return false;
        }
        Attribute attribute = (Attribute) o;
        return getName().equalsIgnoreCase(attribute.getName());
    }

    @Override
    public int compareTo(InterfaceHttpData arg0) {
        if (!(arg0 instanceof InternalAttribute)) {
            throw new ClassCastException("Cannot compare " + getHttpDataType() +
                    " with " + arg0.getHttpDataType());
        }
        return compareTo((InternalAttribute) arg0);
    }

    public int compareTo(InternalAttribute o) {
        return getName().compareToIgnoreCase(o.getName());
    }

    public int size() {
        int size = 0;
        for (String elt : value) {
            size += elt.length();
        }
        return size;
    }
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (String elt : value) {
            result.append(elt);
        }
        return result.toString();
    }

    @Override
    public String getName() {
        return "InternalAttribute";
    }
}
