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
package org.jboss.netty.handler.codec.spdy;

import org.jboss.netty.util.internal.StringUtil;

/**
 * The default {@link SpdyPingFrame} implementation.
 */
public class DefaultSpdyPingFrame implements SpdyPingFrame {

    private int id;

    /**
     * Creates a new instance.
     *
     * @param id the unique ID of this frame
     */
    public DefaultSpdyPingFrame(int id) {
        setId(id);
    }

    @Deprecated
    public int getID() {
        return getId();
    }

    public int getId() {
        return id;
    }

    @Deprecated
    public void setID(int id) {
        setId(id);
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(getClass().getSimpleName());
        buf.append(StringUtil.NEWLINE);
        buf.append("--> ID = ");
        buf.append(id);
        return buf.toString();
    }
}
