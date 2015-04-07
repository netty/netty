/*
 * Copyright 2015 The Netty Project
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
package io.netty.example.udt.file;

/**
 * The {@link UdtFileMessage} which signals the end of the content batch.
 */
public class LastUdtFileMessage extends UdtFileMessage {
    @Override
    public byte magic() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setMagic(byte magic) {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte opcode() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setOpcode(byte opcode) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int fileLength() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setFileLength(int fileSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int messageLength() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setMessageLength(int messageLength) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String message() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setMessage(String message) {
        throw new UnsupportedOperationException();
    }
}
