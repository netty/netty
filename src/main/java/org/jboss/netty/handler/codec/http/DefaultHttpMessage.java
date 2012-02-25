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

import java.util.*;

import static org.jboss.netty.handler.codec.http.HttpMessageDecoder.*;

/**
 * The default {@link HttpMessage} implementation.
 */
public class DefaultHttpMessage implements HttpMessage {

    private static final List<Map.Entry<String,String>> EMPTY_HEADERS_LIST =
            Collections.unmodifiableList(new ArrayList<Map.Entry<String, String>>(0));
    private static final Set<String> EMPTY_SET = Collections.unmodifiableSet(new HashSet<String>(0));
    private static final List<String> EMPTY_LIST = Collections.unmodifiableList(new ArrayList<String>(0));
    private HttpHeaders headers;
    private HttpVersion version;
    private ChannelBuffer content = ChannelBuffers.EMPTY_BUFFER;
    private boolean chunked;
    private ChannelBuffer headerBlock;
    private int mapOffset;
    private int numHeaders;

    /**
     * Creates a new instance.
     */
    protected DefaultHttpMessage(final HttpVersion version) {
        setProtocolVersion(version);
    }

    private void ensureHeaders() {
        if (headers == null) {
            headers = new HttpHeaders();
        }
    }

    public void addHeader(final String name, final Object value) {
        ensureHeaders();
        headers.addHeader(name, value);
    }

    public void setHeader(final String name, final Object value) {
        ensureHeaders();
        headers.setHeader(name, value);
    }

    public void setHeader(final String name, final Iterable<?> values) {
        ensureHeaders();
        headers.setHeader(name, values);
    }

    public void removeHeader(final String name) {
        if (headers != null) {
            headers.removeHeader(name);
        }
        if (headerBlock != null) {
            int length = name.length();
            for (int i = 0; i < numHeaders; i++) {
                int base = mapOffset + i * INT_SIZE * POSITIONS;
                int nameStart = headerBlock.getInt(base);
                int nameLength = headerBlock.getInt(base + NAME_END) - nameStart;
                if (length == nameLength) {
                    int valueLength = headerBlock.getInt(base + VALUE_END) - headerBlock.getInt(base + VALUE_START);
                    if (valueLength >= 0) {
                        if (checkEquals(name, nameStart, nameLength)) {
                            // Remove the name
                            headerBlock.setInt(base + NAME_END, nameStart);
                        }
                    }
                }
            }
        }
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
        return chunked || HttpCodecUtil.isTransferEncodingChunked(this);
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

    public void setHeaderBlock(ChannelBuffer headerBlock, int mapOffset, int numHeaders) {
        this.headerBlock = headerBlock;
        this.mapOffset = mapOffset;
        this.numHeaders = numHeaders;
    }

    public void clearHeaders() {
        if (headers != null) {
            headers.clearHeaders();
        }
        headerBlock = null;
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
        String header = headers == null ? null : headers.getHeader(name);
        if (header == null && headerBlock != null) {
            int length = name.length();
            for (int i = 0; i < numHeaders; i++) {
                int base = mapOffset + i * INT_SIZE * POSITIONS;
                int nameStart = headerBlock.getInt(base);
                int nameLength = headerBlock.getInt(base + NAME_END) - nameStart;
                if (length == nameLength) {
                    int valueStart = headerBlock.getInt(base + VALUE_START);
                    int valueLength = headerBlock.getInt(base + VALUE_END) - valueStart;
                    if (valueLength >= 0) {
                        if (checkEquals(name, nameStart, nameLength)) {
                            return new String(getHeaderBytes(valueStart, valueLength));
                        }
                    }
                }
            }
        }
        return header;
    }

    private boolean checkEquals(String name, int nameStart, int nameLength) {
        for (int i = 0; i < nameLength; i++) {
            if (name.charAt(i) != headerBlock.getByte(nameStart + i)) {
                return false;
            }
        }
        return true;
    }

    public List<String> getHeaders(final String name) {
        if (headerBlock != null) {
            List<String> list = new ArrayList<String>();
            int length = name.length();
            for (int i = 0; i < numHeaders; i++) {
                int base = mapOffset + i * INT_SIZE * POSITIONS;
                int nameStart = headerBlock.getInt(base);
                int nameLength = headerBlock.getInt(base + NAME_END) - nameStart;
                if (length == nameLength) {
                    int valueStart = headerBlock.getInt(base + VALUE_START);
                    int valueLength = headerBlock.getInt(base + VALUE_END) - valueStart;
                    if (valueLength >= 0) {
                        if (checkEquals(name, nameStart, nameLength)) {
                            list.add(new String(getHeaderBytes(valueStart, valueLength)));
                        }
                    }
                }
            }
            if (headers != null) {
                list.addAll(headers.getHeaders(name));
            }
            return list;
        } else {
            return headers == null ? EMPTY_LIST : headers.getHeaders(name);
        }
    }

    public List<Map.Entry<String, String>> getHeaders() {
        if (headerBlock != null) {
            List<Map.Entry<String, String>> list = new ArrayList<Map.Entry<String, String>>();
            for (int i = 0; i < numHeaders; i++) {
                int base = mapOffset + i * INT_SIZE * POSITIONS;
                int nameStart = headerBlock.getInt(base);
                int nameLength = headerBlock.getInt(base + NAME_END) - nameStart;
                int valueStart = headerBlock.getInt(base + VALUE_START);
                int valueLength = headerBlock.getInt(base + VALUE_END) - valueStart;
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
            if (headers != null) {
                list.addAll(headers.getHeaders());
            }
            return list;
        } else {
            return headers == null ? EMPTY_HEADERS_LIST : headers.getHeaders();
        }
    }

    public boolean containsHeader(final String name) {
        if (headers != null && headers.containsHeader(name)) {
            return true;
        } else {
            if (headerBlock != null) {
                int length = name.length();
                for (int i = 0; i < numHeaders; i++) {
                    int base = mapOffset + i * INT_SIZE * POSITIONS;
                    int nameStart = headerBlock.getInt(base);
                    int nameLength = headerBlock.getInt(base + NAME_END) - nameStart;
                    if (length == nameLength) {
                        int valueLength = headerBlock.getInt(base + VALUE_END) - headerBlock.getInt(base + VALUE_START);
                        if (valueLength >= 0) {
                            if (checkEquals(name, nameStart, nameLength)) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }
    }

    public Set<String> getHeaderNames() {
        if (headerBlock != null) {
            Set<String> headerNames = new HashSet<String>();
            for (int i = 0; i < numHeaders; i++) {
                int base = mapOffset + i * INT_SIZE * POSITIONS;
                int nameStart = headerBlock.getInt(base);
                int nameLength = headerBlock.getInt(base + NAME_END) - nameStart;
                int valueLength = headerBlock.getInt(base + VALUE_END) - headerBlock.getInt(base + VALUE_START);
                if (nameLength > 0 && valueLength >= 0) {
                    headerNames.add(new String(getHeaderBytes(nameStart, nameLength)));
                }
            }
            if (headers != null) {
                headerNames.addAll(headers.getHeaderNames());
            }
            return headerNames;
        } else {
            return headers == null ? EMPTY_SET : headers.getHeaderNames();
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
