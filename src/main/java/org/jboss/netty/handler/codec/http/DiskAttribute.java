/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.http;

import java.io.IOException;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

/**
 * Disk implementation of Attributes
 * @author frederic bregier
 *
 */
public class DiskAttribute extends AbstractDiskHttpData implements Attribute {
    public static String baseDirectory = null;

    public static boolean deleteOnExitTemporaryFile = true;

    public static String prefix = "Attr_";

    public static String postfix = ".att";

    /**
     * Constructor used for huge Attribute
     * @param name
     */
    public DiskAttribute(String name) {
        super(name, HttpCodecUtil.DEFAULT_CHARSET, 0);
    }
    /**
     *
     * @param name
     * @param value
     * @throws NullPointerException
     * @throws IllegalArgumentException
     * @throws IOException
     */
    public DiskAttribute(String name, String value)
            throws NullPointerException, IllegalArgumentException, IOException {
        super(name, HttpCodecUtil.DEFAULT_CHARSET, 0); // Attribute have no default size
        setValue(value);
    }

    public HttpDataType getHttpDataType() {
        return HttpDataType.Attribute;
    }

    public String getValue() throws IOException {
        byte [] bytes = get();
        return new String(bytes, charset);
    }

    public void setValue(String value) throws IOException {
        if (value == null) {
            throw new NullPointerException("value");
        }
        byte [] bytes = value.getBytes(charset);
        ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(bytes);
        if (definedSize > 0) {
            definedSize = buffer.readableBytes();
        }
        setContent(buffer);
    }

    @Override
    public void addContent(ChannelBuffer buffer, boolean last) throws IOException {
        int localsize = buffer.readableBytes();
        if (definedSize > 0 && definedSize < size + localsize) {
            definedSize = size + localsize;
        }
        super.addContent(buffer, last);
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

    public int compareTo(InterfaceHttpData arg0) {
        if (!(arg0 instanceof Attribute)) {
            throw new ClassCastException("Cannot compare " + getHttpDataType() +
                    " with " + arg0.getHttpDataType());
        }
        return compareTo((Attribute) arg0);
    }

    public int compareTo(Attribute o) {
        return getName().compareToIgnoreCase(o.getName());
    }

    @Override
    public String toString() {
        try {
            return getName() + "=" + getValue();
        } catch (IOException e) {
            return getName() + "=IoException";
        }
    }

    protected boolean deleteOnExit() {
        return deleteOnExitTemporaryFile;
    }

    protected String getBaseDirectory() {
        return baseDirectory;
    }

    protected String getDiskFilename() {
        return getName()+postfix;
    }

    protected String getPostfix() {
        return postfix;
    }

    protected String getPrefix() {
        return prefix;
    }
}
