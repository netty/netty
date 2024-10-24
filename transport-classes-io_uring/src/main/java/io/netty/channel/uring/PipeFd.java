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
package io.netty.channel.uring;

import io.netty.channel.unix.FileDescriptor;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;

public class PipeFd implements AutoCloseable {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PipeFd.class);

    private FileDescriptor readFd;

    private FileDescriptor writeFd;

    public PipeFd() throws IOException {
        FileDescriptor[] pipe = FileDescriptor.pipe();
        readFd = pipe[0];
        writeFd = pipe[1];
    }

    PipeFd(FileDescriptor readFd, FileDescriptor writeFd) {
        this.readFd = readFd;
        this.writeFd = writeFd;
    }

    public FileDescriptor readFd() {
        return readFd;
    }

    public FileDescriptor writeFd() {
        return writeFd;
    }

    @Override
    public void close() throws Exception {
        try {
            if (readFd != null) {
                readFd.close();
            }
        } catch (IOException e) {
            logger.warn("Error while closing a pipe", e);
        }

        try {
            if (writeFd != null) {
                writeFd.close();
            }
        } catch (IOException e) {
            logger.warn("Error while closing a pipe", e);
        }
    }
}
