/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.spdy;

import io.netty.util.internal.StringUtil;

import java.util.Map;

/**
 * The default {@link SpdyHeadersFrame} implementation.
 */
public class DefaultSpdyHeadersFrame extends DefaultSpdyStreamFrame
        implements SpdyHeadersFrame {

    private boolean invalid;
    private boolean truncated;
    private final SpdyHeaders headers = new DefaultSpdyHeaders();

    /**
     * Creates a new instance.
     *
     * @param streamId the Stream-ID of this frame
     */
    public DefaultSpdyHeadersFrame(int streamId) {
        super(streamId);
    }

    @Override
    public SpdyHeadersFrame setStreamId(int streamId) {
        super.setStreamId(streamId);
        return this;
    }

    @Override
    public SpdyHeadersFrame setLast(boolean last) {
        super.setLast(last);
        return this;
    }

    @Override
    public boolean isInvalid() {
        return invalid;
    }

    @Override
    public SpdyHeadersFrame setInvalid() {
        invalid = true;
        return this;
    }

    @Override
    public boolean isTruncated() {
        return truncated;
    }

    @Override
    public SpdyHeadersFrame setTruncated() {
        truncated = true;
        return this;
    }

    @Override
    public SpdyHeaders headers() {
        return headers;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder()
            .append(StringUtil.simpleClassName(this))
            .append("(last: ")
            .append(isLast())
            .append(')')
            .append(StringUtil.NEWLINE)
            .append("--> Stream-ID = ")
            .append(streamId())
            .append(StringUtil.NEWLINE)
            .append("--> Headers:")
            .append(StringUtil.NEWLINE);
        appendHeaders(buf);

        // Remove the last newline.
        buf.setLength(buf.length() - StringUtil.NEWLINE.length());
        return buf.toString();
    }

    protected void appendHeaders(StringBuilder buf) {
        for (Map.Entry<String, String> e: headers()) {
            buf.append("    ");
            buf.append(e.getKey());
            buf.append(": ");
            buf.append(e.getValue());
            buf.append(StringUtil.NEWLINE);
        }
    }
}
