/*
 * Copyright 2012 The Netty Project
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
package org.jboss.netty.handler.codec.spdy;

import org.jboss.netty.util.internal.StringUtil;

/**
 * The default {@link SpdySynStreamFrame} implementation.
 */
public class DefaultSpdySynStreamFrame extends DefaultSpdyHeaderBlock
        implements SpdySynStreamFrame {

    private int streamID;
    private int associatedToStreamID;
    private byte priority;
    private boolean last;
    private boolean unidirectional;

    /**
     * Creates a new instance.
     *
     * @param streamID             the Stream-ID of this frame
     * @param associatedToStreamID the Associated-To-Stream-ID of this frame
     * @param priority             the priority of the stream
     */
    public DefaultSpdySynStreamFrame(
            int streamID, int associatedToStreamID, byte priority) {
        super();
        setStreamID(streamID);
        setAssociatedToStreamID(associatedToStreamID);
        setPriority(priority);
    }

    public int getStreamID() {
        return streamID;
    }

    public void setStreamID(int streamID) {
        if (streamID <= 0) {
            throw new IllegalArgumentException(
                    "Stream-ID must be positive: " + streamID);
        }
        this.streamID = streamID;
    }

    public int getAssociatedToStreamID() {
        return associatedToStreamID;
    }

    public void setAssociatedToStreamID(int associatedToStreamID) {
        if (associatedToStreamID < 0) {
            throw new IllegalArgumentException(
                    "Associated-To-Stream-ID cannot be negative: " +
                    associatedToStreamID);
        }
        this.associatedToStreamID = associatedToStreamID;
    }

    public byte getPriority() {
        return priority;
    }

    public void setPriority(byte priority) {
        if (priority < 0 || priority > 3) {
            throw new IllegalArgumentException(
                    "Priortiy must be between 0 and 3 inclusive: " + priority);
        }
        this.priority = priority;
    }

    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }

    public boolean isUnidirectional() {
        return unidirectional;
    }

    public void setUnidirectional(boolean unidirectional) {
        this.unidirectional = unidirectional;
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
        buf.append(streamID);
        buf.append(StringUtil.NEWLINE);
        if (associatedToStreamID != 0) {
            buf.append("--> Associated-To-Stream-ID = ");
            buf.append(associatedToStreamID);
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
