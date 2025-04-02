/*
 * Copyright 2024 The Netty Project
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
package io.netty.util.internal;

import org.jetbrains.annotations.NotNull;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class BoundedInputStream extends FilterInputStream {

    private final int maxBytesRead;
    private int numRead;

    public BoundedInputStream(@NotNull InputStream in, int maxBytesRead) {
        super(in);
        this.maxBytesRead = ObjectUtil.checkPositive(maxBytesRead, "maxRead");
    }

    public BoundedInputStream(@NotNull InputStream in) {
        this(in, 8 * 1024);
    }

    @Override
    public int read() throws IOException {
        checkMaxBytesRead();

        int b = super.read();
        if (b != -1) {
            numRead++;
        }
        return b;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        checkMaxBytesRead();

        // Calculate the maximum number of bytes that we should try to read.
        int num = Math.min(len, maxBytesRead - numRead + 1);

        int b = super.read(buf, off, num);

        if (b != -1) {
            numRead += b;
        }
        return b;
    }

    private void checkMaxBytesRead() throws IOException {
        if (numRead > maxBytesRead) {
            throw new IOException("Maximum number of bytes read: " + numRead);
        }
    }
}
