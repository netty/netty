/*
 * Copyright 2025 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;

/**
 * Most decompressor implementations play fast and loose with {@link Decompressor} API contracts. This wrapper makes
 * sure callers follow that contract.
 */
final class DefensiveDecompressor implements Decompressor {
    private final Decompressor delegate;
    private Status status;
    private boolean closed;
    private boolean failed;

    DefensiveDecompressor(Decompressor delegate) {
        this.delegate = delegate;
    }

    @Override
    public Status status() throws DecompressionException {
        checkReady();
        status = delegate.status();
        return status;
    }

    @Override
    public void addInput(ByteBuf buf) throws DecompressionException {
        checkReady();
        checkState(Status.NEED_INPUT);
        try {
            delegate.addInput(buf);
        } catch (Exception e) {
            failed = true;
            throw e;
        }
        status = null;
    }

    @Override
    public void endOfInput() throws DecompressionException {
        checkReady();
        checkState(Status.NEED_INPUT);
        try {
            delegate.endOfInput();
        } catch (Exception e) {
            failed = true;
            throw e;
        }
        status = null;
    }

    @Override
    public ByteBuf takeOutput() throws DecompressionException {
        checkReady();
        checkState(Status.NEED_OUTPUT);
        ByteBuf out;
        try {
            out = delegate.takeOutput();
        } catch (Exception e) {
            failed = true;
            throw e;
        }
        status = null;
        return out;
    }

    @Override
    public void close() {
        if (closed) {
            throw new IllegalStateException("Already closed");
        }
        closed = true;
    }

    private void checkReady() {
        if (closed) {
            throw new IllegalStateException("Already closed");
        }
        if (failed) {
            throw new IllegalStateException("Previous call failed");
        }
    }

    private void checkState(Status expected) {
        if (this.status != expected) {
            throw new IllegalStateException("Not in expected state " + expected + ", was " + this.status);
        }
    }
}
