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
        checkMaxBytesRead(1);
        try {
            int b = super.read();
            if (b <= 0) {
                // We couldn't read anything.
                numRead--;
            }
            return b;
        } catch (IOException e) {
            numRead--;
            throw e;
        }
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        // Calculate the maximum number of bytes that we should try to read.
        int num = Math.min(len, maxBytesRead - numRead + 1);
        checkMaxBytesRead(num);
        try {
            int b = super.read(buf, off, num);
            if (b == -1) {
                // We couldn't read anything.
                numRead -= num;
            } else if (b != num) {
                // Correct numRead based on the actual amount we were able to read.
                numRead -= num - b;
            }
            return b;
        } catch (IOException e) {
            numRead -= num;
            throw e;
        }
    }

    private void checkMaxBytesRead(int n) throws IOException {
        int sum = numRead + n;
        if (sum < 0 || sum > maxBytesRead) {
            numRead = maxBytesRead + 1;
            throw new IOException("Maximum number of bytes read: " + maxBytesRead);
        }
        numRead = sum;
    }
}
