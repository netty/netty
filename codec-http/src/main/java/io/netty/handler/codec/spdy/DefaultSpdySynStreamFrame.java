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

    private final int streamId;
    private final int associatedToStreamId;
    private final byte priority;
    private final boolean last;
    private final boolean unidirectional;

    /**
     * Creates a new instance.
     *
     * @param streamId             the Stream-ID of this frame
     * @param associatedToStreamId the Associated-To-Stream-ID of this frame
     * @param priority             the priority of the stream
     */
    public DefaultSpdySynStreamFrame(
            int streamId, int associatedToStreamId, byte priority) {
        this(streamId, associatedToStreamId, priority, false, false);
    }

    /**
     * Creates a new instance.
     *
     * @param streamId             the Stream-ID of this frame
     * @param associatedToStreamId the Associated-To-Stream-ID of this frame
     * @param priority             the priority of the stream
     * @param unidirectional       if the stream created with this frame is to be
     *                             considered half-closed to the receiver.
     * @param last                 if the frame is the last in the stream
     */
    public DefaultSpdySynStreamFrame(
            int streamId, int associatedToStreamId, byte priority, boolean unidirectional, boolean last) {
        if (streamId <= 0) {
            throw new IllegalArgumentException(
                    "Stream-ID must be positive: " + streamId);
        }
        if (associatedToStreamId < 0) {
            throw new IllegalArgumentException(
                    "Associated-To-Stream-ID cannot be negative: " +
                            associatedToStreamId);
        }
        if (priority < 0 || priority > 7) {
            throw new IllegalArgumentException(
                    "Priority must be between 0 and 7 inclusive: " + priority);
        }
        this.priority = priority;
        this.associatedToStreamId = associatedToStreamId;
        this.streamId = streamId;
        this.last = last;
        this.unidirectional = unidirectional;
    }

    @Override
    public int streamId() {
        return streamId;
    }

    @Override
    public int associatedToStreamId() {
        return associatedToStreamId;
    }

    @Override
    public byte priority() {
        return priority;
    }

    @Override
    public boolean isLast() {
        return last;
    }

    @Override
    public boolean isUnidirectional() {
        return unidirectional;
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
