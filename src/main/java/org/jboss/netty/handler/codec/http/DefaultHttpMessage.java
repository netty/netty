/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.http;


import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.util.internal.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import static org.jboss.netty.handler.codec.http.HttpMessageDecoder.*;

/**
 * The default {@link HttpMessage} implementation.
 */
public class DefaultHttpMessage implements HttpMessage {

    private final HttpHeaders headers = new HttpHeaders();
    private HttpVersion version;
    private ChannelBuffer content = ChannelBuffers.EMPTY_BUFFER;
    private boolean chunked;
    private ChannelBuffer headerBlock;
    private int mapOffset;

    /**
     * Creates a new instance.
     */
    protected DefaultHttpMessage(final HttpVersion version) {
        setProtocolVersion(version);
    }

    public void addHeader(final String name, final Object value) {
        headers.addHeader(name, value);
    }

    public void setHeader(final String name, final Object value) {
        // What does this do when there are multiple headers already?
        headers.setHeader(name, value);
    }

    public void setHeader(final String name, final Iterable<?> values) {
        // What does this do when there are multiple headers already?
        headers.setHeader(name, values);
    }

    public void removeHeader(final String name) {
        headers.removeHeader(name);
    }

    @Deprecated
    public long getContentLength() {
        return HttpHeaders.getContentLength(this);
    }

    @Deprecated
    public long getContentLength(long defaultValue) {
        return HttpHeaders.getContentLength(this, defaultValue);
    }

    public boolean isChunked() {
        if (chunked) {
            return true;
        } else {
            return HttpCodecUtil.isTransferEncodingChunked(this);
        }
    }

    public void setChunked(boolean chunked) {
        this.chunked = chunked;
        if (chunked) {
            setContent(ChannelBuffers.EMPTY_BUFFER);
        }
    }

    @Deprecated
    public boolean isKeepAlive() {
        return HttpHeaders.isKeepAlive(this);
    }

    public void setHeaderBlock(ChannelBuffer headerBlock, int mapOffset, int headers) {
        this.headerBlock = headerBlock;
        this.mapOffset = mapOffset;
    }

    public void clearHeaders() {
        headers.clearHeaders();
    }

    public void setContent(ChannelBuffer content) {
        if (content == null) {
            content = ChannelBuffers.EMPTY_BUFFER;
        }
        if (content.readable() && isChunked()) {
            throw new IllegalArgumentException(
                    "non-empty content disallowed if this.chunked == true");
        }
        this.content = content;
    }

    public String getHeader(final String name) {
        // We can override what we read
        String header = headers.getHeader(name);
        if (header == null && headerBlock != null) {
            for (int i = 0; i < 32; i++) {
                int base = mapOffset + i * INT_SIZE * POSITIONS;
                int nameStart = headerBlock.getInt(base);
                int nameEnd = headerBlock.getInt(base + NAME_END);
                if (headerBlock.getByte(nameEnd) == 0) {
                    break;
                }
                int nameLength = nameEnd - nameStart;
                if (name.length() == nameLength) {
                    int valueStart = headerBlock.getInt(base + VALUE_START);
                    int valueEnd = headerBlock.getInt(base + VALUE_END);
                    int valueLength = valueEnd - valueStart;
                    if (valueLength >= 0) {
                        if (name.equals(new String(getHeaderBytes(nameStart, nameLength)))) {
                            byte[] valueBuffer = getHeaderBytes(valueStart, valueLength);
                            return new String(valueBuffer);
                        }
                    }
                }
            }
        }
        return header;
    }

    public List<String> getHeaders(final String name) {
        List<String> list = new ArrayList<String>();
        if (headerBlock != null) {
            for (int i = 0; i < 32; i++) {
                int base = mapOffset + i * INT_SIZE * POSITIONS;
                int nameStart = headerBlock.getInt(base);
                int nameEnd = headerBlock.getInt(base + NAME_END);
                if (headerBlock.getByte(nameEnd) == 0) {
                    break;
                }
                int nameLength = nameEnd - nameStart;
                if (name.length() == nameLength) {
                    int valueStart = headerBlock.getInt(base + VALUE_START);
                    int valueEnd = headerBlock.getInt(base + VALUE_END);
                    int valueLength = valueEnd - valueStart;
                    if (valueLength >= 0) {
                        if (name.equals(new String(getHeaderBytes(nameStart, nameLength)))) {
                            list.add(new String(getHeaderBytes(valueStart, valueLength)));
                        }
                    }
                }
            }
        }
        list.addAll(headers.getHeaders(name));
        return list;
    }

    public List<Map.Entry<String, String>> getHeaders() {
        if (headerBlock != null) {
            List<Map.Entry<String, String>> list = new ArrayList<Map.Entry<String, String>>();
            for (int i = 0; i < 32; i++) {
                int base = mapOffset + i * INT_SIZE * POSITIONS;
                int nameStart = headerBlock.getInt(base);
                int nameEnd = headerBlock.getInt(base + NAME_END);
                if (headerBlock.getByte(nameEnd) == 0) {
                    break;
                }
                int valueStart = headerBlock.getInt(base + VALUE_START);
                int valueEnd = headerBlock.getInt(base + VALUE_END);
                int nameLength = nameEnd - nameStart;
                int valueLength = valueEnd - valueStart;
                if (nameLength > 0 && valueLength >= 0) {
                    final String headerName = new String(getHeaderBytes(nameStart, nameLength));
                    final String headerValue = new String(getHeaderBytes(valueStart, valueLength));
                    list.add(new Map.Entry<String, String>() {

                        public String getKey() {
                            return headerName;
                        }

                        public String getValue() {
                            return headerValue;
                        }

                        public String setValue(String value) {
                            throw new AssertionError();
                        }
                    });
                }
            }
            list.addAll(headers.getHeaders());
            return list;
        } else {
            return headers.getHeaders();
        }
    }

    public boolean containsHeader(final String name) {
        return getHeader(name) != null;
    }

    public Set<String> getHeaderNames() {
        if (headerBlock != null) {
            Set<String> headerNames = new HashSet<String>();
            for (int i = 0; i < 32; i++) {
                int base = mapOffset + i * INT_SIZE * POSITIONS;
                int nameStart = headerBlock.getInt(base);
                int nameEnd = headerBlock.getInt(base + NAME_END);
                if (headerBlock.getByte(nameEnd) == 0) {
                    break;
                }
                int valueStart = headerBlock.getInt(base + VALUE_START);
                int valueEnd = headerBlock.getInt(base + VALUE_END);
                int nameLength = nameEnd - nameStart;
                int valueLength = valueEnd - valueStart;
                if (nameLength > 0 && valueLength >= 0) {
                    byte[] nameBuffer = getHeaderBytes(nameStart, nameLength);
                    final String headerName = new String(nameBuffer);
                    headerNames.add(headerName);
                }
            }
            headerNames.addAll(headers.getHeaderNames());
            return headerNames;
        } else {
            return headers.getHeaderNames();
        }
    }

    private byte[] getHeaderBytes(int nameStart, int nameLength) {
        byte[] nameBuffer = new byte[nameLength];
        headerBlock.getBytes(nameStart, nameBuffer);
        return nameBuffer;
    }

    public HttpVersion getProtocolVersion() {
        return version;
    }

    public void setProtocolVersion(HttpVersion version) {
        if (version == null) {
            throw new NullPointerException("version");
        }
        this.version = version;
    }

    public ChannelBuffer getContent() {
        return content;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(getClass().getSimpleName());
        buf.append("(version: ");
        buf.append(getProtocolVersion().getText());
        buf.append(", keepAlive: ");
        buf.append(isKeepAlive());
        buf.append(", chunked: ");
        buf.append(isChunked());
        buf.append(')');
        buf.append(StringUtil.NEWLINE);
        appendHeaders(buf);

        // Remove the last newline.
        buf.setLength(buf.length() - StringUtil.NEWLINE.length());
        return buf.toString();
    }

    void appendHeaders(StringBuilder buf) {
        for (Map.Entry<String, String> e: getHeaders()) {
            buf.append(e.getKey());
            buf.append(": ");
            buf.append(e.getValue());
            buf.append(StringUtil.NEWLINE);
        }
    }
}
