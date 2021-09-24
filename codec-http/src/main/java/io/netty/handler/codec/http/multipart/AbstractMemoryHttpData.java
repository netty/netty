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
package io.netty.handler.codec.http.multipart;

import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.BufferAllocator;
import io.netty.buffer.api.CompositeBuffer;
import io.netty.handler.codec.http.HttpConstants;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static java.util.Objects.requireNonNull;

/**
 * Abstract Memory HttpData implementation
 */
public abstract class AbstractMemoryHttpData<R extends AbstractMemoryHttpData<R>> extends AbstractHttpData<R> {

    private Buffer buffer;
    private final BufferAllocator allocator;
    private int chunkPosition;

    protected AbstractMemoryHttpData(BufferAllocator allocator, String name, Charset charset, long size) {
        super(name, charset, size);
        buffer = allocator.allocate(0);
        this.allocator = allocator;
    }

    @Override
    public void setContent(Buffer buffer) throws IOException {
        requireNonNull(buffer, "buffer");
        long localsize = buffer.readableBytes();
        try {
            checkSize(localsize);
        } catch (IOException e) {
            buffer.close();
            throw e;
        }
        if (definedSize > 0 && definedSize < localsize) {
            buffer.close();
            throw new IOException("Out of size: " + localsize + " > " +
                    definedSize);
        }
        if (this.buffer != null) {
            this.buffer.close();
        }
        this.buffer = buffer;
        size = localsize;
        setCompleted();
    }

    @Override
    public void setContent(InputStream inputStream) throws IOException {
        requireNonNull(inputStream, "inputStream");
        byte[] bytes = new byte[4096 * 4];
        Buffer buffer = allocator.allocate(1024);
        int written = 0;
        try {
            int read = inputStream.read(bytes);
            while (read > 0) {
                buffer.ensureWritable(bytes.length);
                buffer.writeBytes(bytes, 0, read);
                written += read;
                checkSize(written);
                read = inputStream.read(bytes);
            }
        } catch (IOException e) {
            buffer.close();
            throw e;
        }
        size = written;
        if (definedSize > 0 && definedSize < size) {
            buffer.close();
            throw new IOException("Out of size: " + size + " > " + definedSize);
        }
        if (this.buffer != null) {
            this.buffer.close();
        }
        this.buffer = buffer;
        setCompleted();
    }

    @Override
    public void addContent(Buffer buffer, boolean last)
            throws IOException {
        if (buffer != null) {
            long localsize = buffer.readableBytes();
            try {
                checkSize(size + localsize);
            } catch (IOException e) {
                buffer.close();
                throw e;
            }
            if (definedSize > 0 && definedSize < size + localsize) {
                buffer.close();
                throw new IOException("Out of size: " + (size + localsize) +
                        " > " + definedSize);
            }
            size += localsize;
            if (this.buffer == null) {
                this.buffer = buffer;
            } else if (localsize == 0) {
                // Nothing to add and byteBuf already exists
                buffer.close();
            } else if (this.buffer.readableBytes() == 0) {
                // Previous buffer is empty, so just replace it
                this.buffer.close();
                this.buffer = buffer;
            } else if (CompositeBuffer.isComposite(this.buffer)) {
                CompositeBuffer cbb = (CompositeBuffer) this.buffer;
                cbb.extendWith(buffer.send());
            } else {
                this.buffer = CompositeBuffer.compose(allocator, this.buffer.send(), buffer.send());
            }
        }
        if (last) {
            setCompleted();
        } else {
            requireNonNull(buffer, "buffer");
        }
    }

    @Override
    public void setContent(File file) throws IOException {
        requireNonNull(file, "file");
        long newsize = file.length();
        if (newsize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("File too big to be loaded in memory");
        }
        checkSize(newsize);
        RandomAccessFile accessFile = new RandomAccessFile(file, "r");
        ByteBuffer byteBuffer;
        try {
            try (FileChannel fileChannel = accessFile.getChannel()) {
                byte[] array = new byte[(int) newsize];
                byteBuffer = ByteBuffer.wrap(array);
                int read = 0;
                while (read < newsize) {
                    read += fileChannel.read(byteBuffer);
                }
            }
        } finally {
            accessFile.close();
        }
        byteBuffer.flip();
        if (buffer != null) {
            buffer.close();
        }
        final Buffer buffer = allocator.allocate(byteBuffer.remaining());
        while (byteBuffer.hasRemaining()) {
            buffer.writeByte(byteBuffer.get());
        }
        this.buffer = CompositeBuffer.compose(allocator, buffer.send());
        size = newsize;
        setCompleted();
    }

    @Override
    public void delete() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
    }

    @Override
    public byte[] get() {
        if (buffer == null) {
            return EMPTY_BUFFER.array();
        }
        byte[] array = new byte[buffer.readableBytes()];
        buffer.copyInto(buffer.readerOffset(), array, 0, buffer.readableBytes());
        return array;
    }

    @Override
    public String getString() {
        return getString(HttpConstants.DEFAULT_CHARSET);
    }

    @Override
    public String getString(Charset encoding) {
        if (buffer == null) {
            return "";
        }
        if (encoding == null) {
            encoding = HttpConstants.DEFAULT_CHARSET;
        }
        return buffer.toString(encoding);
    }

    /**
     * Utility to go from a In Memory FileUpload
     * to a Disk (or another implementation) FileUpload
     * @return the attached ByteBuf containing the actual bytes
     */
    @Override
    public Buffer getBuffer() {
        return buffer;
    }

    @Override
    public Buffer getChunk(int length) throws IOException {
        if (buffer == null || length == 0 || buffer.readableBytes() == 0) {
            chunkPosition = 0;
            return allocator.allocate(0);
        }
        int sizeLeft = buffer.readableBytes() - chunkPosition;
        if (sizeLeft == 0) {
            chunkPosition = 0;
            return allocator.allocate(0);
        }
        int sliceLength = length;
        if (sizeLeft < length) {
            sliceLength = sizeLeft;
        }
        Buffer chunk = buffer.copy(chunkPosition, sliceLength);
        chunkPosition += sliceLength;
        return chunk;
    }

    @Override
    public boolean isInMemory() {
        return true;
    }

    @Override
    public boolean renameTo(File dest) throws IOException {
        requireNonNull(dest, "dest");
        if (buffer == null) {
            // empty file
            if (!dest.createNewFile()) {
                throw new IOException("file exists already: " + dest);
            }
            return true;
        }
        final int roff = buffer.readerOffset();
        final int readableBytes = buffer.readableBytes();
        try (RandomAccessFile accessFile = new RandomAccessFile(dest, "rw")) {
            try (FileChannel fileChannel = accessFile.getChannel()) {
                buffer.forEachReadable(0, (index, component) -> {
                    buffer.skipReadable(fileChannel.write(component.readableBuffer()));
                    return true;
                });
                fileChannel.force(false);
            }
        }
        return buffer.readerOffset() - roff == readableBytes;
    }

    @Override
    public File getFile() throws IOException {
        throw new IOException("Not represented by a file");
    }

    protected BufferAllocator allocator() {
        return allocator;
    }
}
