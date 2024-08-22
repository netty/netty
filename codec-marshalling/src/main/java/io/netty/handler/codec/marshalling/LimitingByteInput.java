/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.marshalling;

import static io.netty.util.internal.ObjectUtil.checkPositive;

import org.jboss.marshalling.ByteInput;

import java.io.IOException;

/**
 * {@link ByteInput} implementation which wraps another {@link ByteInput} and throws a {@link TooBigObjectException}
 * if the read limit was reached.
 */
class LimitingByteInput implements ByteInput {

    // Use a static instance here to remove the overhead of fillStacktrace
    private static final TooBigObjectException EXCEPTION = new TooBigObjectException();

    private final ByteInput input;
    private final long limit;
    private long read;

    LimitingByteInput(ByteInput input, long limit) {
        this.input = input;
        this.limit = checkPositive(limit, "limit");
    }

    @Override
    public void close() throws IOException {
        // Nothing to do
    }

    @Override
    public int available() throws IOException {
        return readable(input.available());
    }

    @Override
    public int read() throws IOException {
        int readable = readable(1);
        if (readable > 0) {
            int b = input.read();
            read++;
            return b;
        } else {
            throw EXCEPTION;
        }
    }

    @Override
    public int read(byte[] array) throws IOException {
        return read(array, 0, array.length);
    }

    @Override
    public int read(byte[] array, int offset, int length) throws IOException {
        int readable = readable(length);
        if (readable > 0) {
            int i = input.read(array, offset, readable);
            read += i;
            return i;
        } else {
            throw EXCEPTION;
        }
    }

    @Override
    public long skip(long bytes) throws IOException {
        int readable = readable((int) bytes);
        if (readable > 0) {
            long i = input.skip(readable);
            read += i;
            return i;
        } else {
            throw EXCEPTION;
        }
    }

    private int readable(int length) {
        return (int) Math.min(length, limit - read);
    }

    /**
     * Exception that will get thrown if the {@link Object} is too big to unmarshall
     *
     */
    static final class TooBigObjectException extends IOException {
        private static final long serialVersionUID = 1L;
    }
}
