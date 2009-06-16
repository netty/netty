/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.stream;

import static org.jboss.netty.buffer.ChannelBuffers.*;

import java.io.InputStream;
import java.io.PushbackInputStream;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class ChunkedStream implements ChunkedInput {

    static final int DEFAULT_CHUNK_SIZE = 8192;

    private final PushbackInputStream in;
    private final int chunkSize;
    private volatile long offset;

    public ChunkedStream(InputStream in) {
        this(in, DEFAULT_CHUNK_SIZE);
    }

    public ChunkedStream(InputStream in, int chunkSize) {
        if (in == null) {
            throw new NullPointerException("in");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException(
                    "chunkSize: " + chunkSize +
                    " (expected: a positive integer)");
        }

        if (in instanceof PushbackInputStream) {
            this.in = (PushbackInputStream) in;
        } else {
            this.in = new PushbackInputStream(in);
        }
        this.chunkSize = chunkSize;
    }

    public long getCurrentRelativeOffset() {
        return offset;
    }

    public boolean available() throws Exception {
        int b = in.read();
        if (b < 0) {
            return false;
        } else {
            in.unread(b);
            return true;
        }
    }

    public void close() throws Exception {
        in.close();
    }

    public Object readChunk() throws Exception {
        if (!available()) {
            return null;
        }

        int chunkSize = Math.min(this.chunkSize, in.available());
        byte[] chunk = new byte[chunkSize];
        int readBytes = 0;
        for (;;) {
            int localReadBytes = in.read(chunk, readBytes, chunkSize - readBytes);
            if (localReadBytes < 0) {
                break;
            }
            readBytes += localReadBytes;
            offset += readBytes;
        }

        return wrappedBuffer(chunk, 0, readBytes);
    }
}
