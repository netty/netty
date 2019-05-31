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

package io.netty.handler.codec.quic.packet;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.quic.Version;

import java.util.Arrays;

public class RetryPacket extends Packet implements LongPacket {

    protected Version version;
    protected byte[] sourceConnectionID;
    protected byte[] originalConnectionID;
    protected byte[] retryToken;

    RetryPacket() {}

    public RetryPacket(Version version, byte[] sourceConnectionID, byte[] originalConnectionID, byte[] retryToken) {
        this.version = version;
        this.sourceConnectionID = sourceConnectionID;
        this.originalConnectionID = originalConnectionID;
        this.retryToken = retryToken;
    }

    @Override
    public Version version() {
        return version;
    }

    @Override
    public void version(Version version) {
        this.version = version;
    }

    @Override
    public FullPacketType packetType() {
        return FullPacketType.RETRY;
    }

    @Override
    public byte[] sourceConnectionID() {
        return sourceConnectionID;
    }

    @Override
    public void sourceConnectionID(byte[] sourceConnectionID) {
        this.sourceConnectionID = sourceConnectionID;
    }

    public byte[] originalConnectionID() {
        return originalConnectionID;
    }

    public void originalConnectionID(byte[] originalConnectionID) {
        this.originalConnectionID = originalConnectionID;
    }

    @Override
    public void read(ByteBuf buf) {
        byte[][] connectionInfo = HeaderUtil.readConnectionIDInfo(buf);
        connectionID = connectionInfo[0];
        sourceConnectionID = connectionInfo[1];

        originalConnectionID = HeaderUtil.read(buf, ((firstByte & 0xFF) & 0xf) + 3);

        retryToken = drain(buf);
    }

    @Override
    public void write(ByteBuf buf) {
        //TODO
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RetryPacket that = (RetryPacket) o;

        if (version != that.version) return false;
        if (!Arrays.equals(sourceConnectionID, that.sourceConnectionID)) return false;
        if (!Arrays.equals(originalConnectionID, that.originalConnectionID)) return false;
        return Arrays.equals(retryToken, that.retryToken);
    }

    @Override
    public int hashCode() {
        int result = version != null ? version.hashCode() : 0;
        result = 31 * result + Arrays.hashCode(sourceConnectionID);
        result = 31 * result + Arrays.hashCode(originalConnectionID);
        result = 31 * result + Arrays.hashCode(retryToken);
        return result;
    }
}
