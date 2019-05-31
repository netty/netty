/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.codec.quic.frame;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.quic.QuicDecoder;
import io.netty.handler.codec.quic.QuicEncoder;

public class CloseFrame extends QuicFrame {

    private short errorCode;
    private String error;
    private boolean application;

    public CloseFrame(short errorCode, String error, boolean application) {
        super(application ? FrameType.APPLICATION_CLOSE : FrameType.CONNECTION_CLOSE, (byte) -1);
        this.errorCode = errorCode;
        this.error = error;
        this.application = application;
    }

    public CloseFrame(boolean application) {
        super(application ? FrameType.APPLICATION_CLOSE : FrameType.CONNECTION_CLOSE, (byte) -1);
        this.application = application;
    }

    @Override
    public void read(ByteBuf buf) {
        super.read(buf);
        errorCode = buf.readShort();
        error = QuicDecoder.decodeString(buf);
    }

    @Override
    public void write(ByteBuf buf) {
        super.write(buf);
        buf.writeShort(errorCode);
        QuicEncoder.writeString(buf, error);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        CloseFrame that = (CloseFrame) o;

        if (errorCode != that.errorCode) return false;
        if (application != that.application) return false;
        return error != null ? error.equals(that.error) : that.error == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (int) errorCode;
        result = 31 * result + (error != null ? error.hashCode() : 0);
        result = 31 * result + (application ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "CloseFrame{" +
                "errorCode=" + errorCode +
                ", error='" + error + '\'' +
                ", application=" + application +
                ", typeByte=" + typeByte +
                '}';
    }

    public short errorCode() {
        return errorCode;
    }

    public String error() {
        return error;
    }

    public boolean application() {
        return application;
    }

    public void errorCode(short errorCode) {
        this.errorCode = errorCode;
    }

    public void error(String error) {
        this.error = error;
    }

    public void application(boolean application) {
        this.application = application;
        type = application ? FrameType.APPLICATION_CLOSE : FrameType.CONNECTION_CLOSE;
    }
}
