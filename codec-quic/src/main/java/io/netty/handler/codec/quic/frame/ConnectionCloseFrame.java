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
import io.netty.handler.codec.quic.TransportError;
import io.netty.handler.codec.quic.VarInt;

public class ConnectionCloseFrame extends ApplicationCloseFrame {

    private byte errorFrame;

    public ConnectionCloseFrame(TransportError errorCode, String error, byte errorFrame) {
        this(errorCode.errorCode(), error, errorFrame);
    }

    public ConnectionCloseFrame(short errorCode, String error, byte errorFrame) {
        super(errorCode, error);
        this.errorFrame = errorFrame;
    }

    ConnectionCloseFrame() {
        super((byte) 0x1c);
    }

    @Override
    protected void beforeMessageRead(ByteBuf buf) {
        errorFrame = VarInt.read(buf).asByte();
    }

    @Override
    protected void beforeMessageWrite(ByteBuf buf) {
        VarInt.byLong(errorFrame).write(buf);
    }

    public byte errorFrame() {
        return errorFrame;
    }

    public TransportError transportError() {
        return TransportError.byErrorCode((byte) errorCode);
    }

    public void transportError(TransportError error) {
        errorCode = error.errorCode();
    }

    public FrameType errorFrameType() {
        return FrameType.typeById(errorFrame);
    }

    public void errorFrame(byte errorFrame) {
        this.errorFrame = errorFrame;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ConnectionCloseFrame that = (ConnectionCloseFrame) o;

        return errorFrame == that.errorFrame;
    }

    @Override
    public boolean application() {
        return false;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (int) errorFrame;
        return result;
    }

    @Override
    public String toString() {
        return "ConnectionCloseFrame{" +
                "errorFrame=" + errorFrame +
                ", errorCode=" + errorCode +
                ", error='" + error + '\'' +
                '}';
    }

}
