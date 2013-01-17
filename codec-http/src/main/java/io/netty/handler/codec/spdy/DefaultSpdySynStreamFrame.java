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
package io.netty.handler.codec.spdy;

import io.netty.util.internal.StringUtil;

/**
 * The default {@link SpdySynStreamFrame} implementation.
 */
public class DefaultSpdySynStreamFrame extends DefaultSpdyHeaderBlock
        implements SpdySynStreamFrame {

    private int streamId;
    private int associatedToStreamId;
    private byte priority;
    private boolean last;
    private boolean unidirectional;

    /**
     * Creates a new instance.
     *
     * @param streamId             the Stream-ID of this frame
     * @param associatedToStreamId the Associated-To-Stream-ID of this frame
     * @param priority             the priority of the stream
     */
    public DefaultSpdySynStreamFrame(
            int streamId, int associatedToStreamId, byte priority) {
        setStreamId(streamId);
        setAssociatedToStreamId(associatedToStreamId);
        setPriority(priority);
    }

    @Override
    public int getStreamId() {
        return streamId;
    }

    @Override
    public SpdySynStreamFrame setStreamId(int streamId) {
        if (streamId <= 0) {
            throw new IllegalArgumentException(
                    "Stream-ID must be positive: " + streamId);
        }
        this.streamId = streamId;
        return this;
    }

    @Override
    public int getAssociatedToStreamId() {
        return associatedToStreamId;
    }

    @Override
    public SpdySynStreamFrame setAssociatedToStreamId(int associatedToStreamId) {
        if (associatedToStreamId < 0) {
            throw new IllegalArgumentException(
                    "Associated-To-Stream-ID cannot be negative: " +
                    associatedToStreamId);
        }
        this.associatedToStreamId = associatedToStreamId;
        return this;
    }

    @Override
    public byte getPriority() {
        return priority;
    }

    @Override
    public SpdySynStreamFrame setPriority(byte priority) {
        if (priority < 0 || priority > 7) {
            throw new IllegalArgumentException(
                    "Priority must be between 0 and 7 inclusive: " + priority);
        }
        this.priority = priority;
        return this;
    }

    @Override
    public boolean isLast() {
        return last;
    }

    @Override
    public SpdySynStreamFrame setLast(boolean last) {
        this.last = last;
        return this;
    }

    @Override
    public boolean isUnidirectional() {
        return unidirectional;
    }

    @Override
    public SpdySynStreamFrame setUnidirectional(boolean unidirectional) {
        this.unidirectional = unidirectional;
        return this;
    }

    @Override
    public SpdySynStreamFrame setInvalid() {
        super.setInvalid();
        return this;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(getClass().getSimpleName());
        buf.append("(last: ");
        buf.append(isLast());
        buf.append("; unidirectional: ");
        buf.append(isUnidirectional());
        buf.append(')');
        buf.append(StringUtil.NEWLINE);
        buf.append("--> Stream-ID = ");
        buf.append(streamId);
        buf.append(StringUtil.NEWLINE);
        if (associatedToStreamId != 0) {
            buf.append("--> Associated-To-Stream-ID = ");
            buf.append(associatedToStreamId);
            buf.append(StringUtil.NEWLINE);
        }
        buf.append("--> Priority = ");
        buf.append(priority);
        buf.append(StringUtil.NEWLINE);
        buf.append("--> Headers:");
        buf.append(StringUtil.NEWLINE);
        appendHeaders(buf);

        // Remove the last newline.
        buf.setLength(buf.length() - StringUtil.NEWLINE.length());
        return buf.toString();
    }
}
