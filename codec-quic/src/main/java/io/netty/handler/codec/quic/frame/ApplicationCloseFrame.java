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
import io.netty.handler.codec.quic.QuicDecoder;
import io.netty.handler.codec.quic.QuicEncoder;

public class ApplicationCloseFrame extends QuicFrame {

    protected short errorCode;
    protected String error;

    public ApplicationCloseFrame(short errorCode, String error) {
        this((byte) 0x1d);
        this.errorCode = errorCode;
        this.error = error;
    }

    protected ApplicationCloseFrame(byte type, short errorCode, String error) {
        this(type);
        this.errorCode = errorCode;
        this.error = error;
    }

    protected ApplicationCloseFrame(byte type) {
        super(FrameType.CONNECTION_CLOSE, type);
    }

    ApplicationCloseFrame() {
        this((byte) 0x1d);
    }

    @Override
    public void read(ByteBuf buf) {
        errorCode = buf.readShort();
        beforeMessageRead(buf);
        error = QuicDecoder.decodeString(buf);
    }

    @Override
    public void write(ByteBuf buf) {
        super.write(buf);
        buf.writeShort(errorCode);
        beforeMessageWrite(buf);
        QuicEncoder.writeString(buf, error);
    }

    protected void beforeMessageRead(ByteBuf buf) {}
    protected void beforeMessageWrite(ByteBuf buf) {}

    public boolean application() {
        return true;
    }

    public short errorCode() {
        return errorCode;
    }

    public void errorCode(short errorCode) {
        this.errorCode = errorCode;
    }

    public String error() {
        return error;
    }

    public void error(String error) {
        this.error = error;
    }

    @Override
    public String toString() {
        return "ApplicationCloseFrame{" +
                "errorCode=" + errorCode +
                ", error='" + error + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ApplicationCloseFrame that = (ApplicationCloseFrame) o;

        if (errorCode != that.errorCode) return false;
        return error != null ? error.equals(that.error) : that.error == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (int) errorCode;
        result = 31 * result + (error != null ? error.hashCode() : 0);
        result = 31 * result + (application() ? 1 : 0);
        return result;
    }
}
