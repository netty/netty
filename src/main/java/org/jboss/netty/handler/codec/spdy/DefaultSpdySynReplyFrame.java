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
 * The default {@link SpdySynReplyFrame} implementation.
 */
public class DefaultSpdySynReplyFrame extends DefaultSpdyHeaderBlock
        implements SpdySynReplyFrame {

    private int streamID;
    private boolean last;

    /**
     * Creates a new instance.
     *
     * @param streamID the Stream-ID of this frame
     */
    public DefaultSpdySynReplyFrame(int streamID) {
        super();
        setStreamID(streamID);
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

    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(getClass().getSimpleName());
        buf.append("(last: ");
        buf.append(isLast());
        buf.append(')');
        buf.append(StringUtil.NEWLINE);
        buf.append("--> Stream-ID = ");
        buf.append(streamID);
        buf.append(StringUtil.NEWLINE);
        buf.append("--> Headers:");
        buf.append(StringUtil.NEWLINE);
        appendHeaders(buf);

        // Remove the last newline.
        buf.setLength(buf.length() - StringUtil.NEWLINE.length());
        return buf.toString();
    }
}
