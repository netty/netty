/*
 *
 *  * Copyright 2019 The Netty Project
 *  *
 *  * The Netty Project licenses this file to you under the Apache License,
 *  * version 2.0 (the "License"); you may not use this file except in compliance
 *  * with the License. You may obtain a copy of the License at:
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  * License for the specific language governing permissions and limitations
 *  * under the License.
 *
 */
package io.netty.handler.codec.quic.frame;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.quic.packet.HeaderUtil;

import java.util.Arrays;

//8 byte
public class PathFrame extends QuicFrame {

    private byte[] data;

    PathFrame(FrameType type) {
        super(type);
    }

    public PathFrame(boolean challenge, byte[] data) {
        super(challenge ? FrameType.PATH_CHALLENGE : FrameType.PATH_RESPONSE);
        this.data = data;
    }

    @Override
    public void read(ByteBuf buf) {
        data = HeaderUtil.read(buf, 8);
    }

    @Override
    public void write(ByteBuf buf) {
        super.write(buf);
        buf.writeBytes(data);
    }

    public byte[] data() {
        return data;
    }

    public void data(byte[] data) {
        this.data = data;
    }

    public boolean isChallenge() {
        return type == FrameType.PATH_CHALLENGE;
    }

    public boolean isResponse() {
        return type == FrameType.PATH_RESPONSE;
    }

    @Override
    public String toString() {
        return "PathFrame{" +
                "data=" + Arrays.toString(data) +
                ", type=" + type +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        PathFrame pathFrame = (PathFrame) o;

        return Arrays.equals(data, pathFrame.data);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }

}
