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
 * XML processing instruction
 */
public class XmlProcessingInstruction {

    private final String data;
    private final String target;

    public XmlProcessingInstruction(String data, String target) {
        this.data = data;
        this.target = target;
    }

    public String data() {
        return data;
    }

    public String target() {
        return target;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        XmlProcessingInstruction that = (XmlProcessingInstruction) o;
        return Objects.equals(data, that.data) &&
                Objects.equals(target, that.target);
    }

    @Override
    public int hashCode() {
        return hash(data, target);
    }

    @Override
    public String toString() {
        return "XmlProcessingInstruction{" +
                "data='" + data + '\'' +
                ", target='" + target + '\'' +
                '}';
    }

}
