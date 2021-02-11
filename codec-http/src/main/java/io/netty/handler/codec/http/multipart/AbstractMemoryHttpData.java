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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.util.internal.ObjectUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.buffer.Unpooled.buffer;
import static io.netty.buffer.Unpooled.compositeBuffer;
import static io.netty.buffer.Unpooled.wrappedBuffer;

/**
 * Abstract Memory HttpData implementation
 */
public abstract class AbstractMemoryHttpData extends AbstractHttpData {

    private ByteBuf byteBuf;
    private int chunkPosition;

    protected AbstractMemoryHttpData(String name, Charset charset, long size) {
        super(name, charset, size);
    }

    @Override
    public void setContent(ByteBuf buffer) throws IOException {
        ObjectUtil.checkNotNull(buffer, "buffer");
        long localsize = buffer.readableBytes();
        checkSize(localsize);
        if (definedSize > 0 && definedSize < localsize) {
            throw new IOException("Out of size: " + localsize + " > " +
                    definedSize);
        }
        if (byteBuf != null) {
            byteBuf.release();
        }
        byteBuf = buffer;
        size = localsize;
        setCompleted();
    }

    @Override
    public void setContent(InputStream inputStream) throws IOException {
        ObjectUtil.checkNotNull(inputStream, "inputStream");

        byte[] bytes = new byte[4096 * 4];
        ByteBuf buffer = buffer();
        int written = 0;
        try {
            int read = inputStream.read(bytes);
            while (read > 0) {
                buffer.writeBytes(bytes, 0, read);
                written += read;
                checkSize(written);
                read = inputStream.read(bytes);
            }
        } catch (IOException e) {
            buffer.release();
            throw e;
        }
        size = written;
        if (definedSize > 0 && definedSize < size) {
            buffer.release();
            throw new IOException("Out of size: " + size + " > " + definedSize);
        }
        if (byteBuf != null) {
            byteBuf.release();
        }
        byteBuf = buffer;
        setCompleted();
    }

    @Override
    public void addContent(ByteBuf buffer, boolean last)
            throws IOException {
        if (buffer != null) {
            long localsize = buffer.readableBytes();
            checkSize(size + localsize);
            if (definedSize > 0 && definedSize < size + localsize) {
                throw new IOException("Out of size: " + (size + localsize) +
                        " > " + definedSize);
            }
            size += localsize;
            if (byteBuf == null) {
                byteBuf = buffer;
            } else if (localsize == 0) {
                // Nothing to add and byteBuf already exists
                buffer.release();
            } else if (byteBuf instanceof CompositeByteBuf) {
                CompositeByteBuf cbb = (CompositeByteBuf) byteBuf;
                cbb.addComponent(true, buffer);
            } else {
                CompositeByteBuf cbb = compositeBuffer(Integer.MAX_VALUE);
                cbb.addComponents(true, byteBuf, buffer);
                byteBuf = cbb;
            }
        }
        if (last) {
            setCompleted();
        } else {
            ObjectUtil.checkNotNull(buffer, "buffer");
        }
    }

    @Override
    public void setContent(File file) throws IOException {
        ObjectUtil.checkNotNull(file, "file");

        long newsize = file.length();
        if (newsize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("File too big to be loaded in memory");
        }
        checkSize(newsize);
        RandomAccessFile accessFile = new RandomAccessFile(file, "r");
        ByteBuffer byteBuffer;
        try {
            FileChannel fileChannel = accessFile.getChannel();
            try {
                byte[] array = new byte[(int) newsize];
                byteBuffer = ByteBuffer.wrap(array);
                int read = 0;
                while (read < newsize) {
                    read += fileChannel.read(byteBuffer);
                }
            } finally {
                fileChannel.close();
            }
        } finally {
            accessFile.close();
        }
        byteBuffer.flip();
        if (byteBuf != null) {
            byteBuf.release();
        }
        byteBuf = wrappedBuffer(Integer.MAX_VALUE, byteBuffer);
        size = newsize;
        setCompleted();
    }

    @Override
    public void delete() {
        if (byteBuf != null) {
            byteBuf.release();
            byteBuf = null;
        }
    }

    @Override
    public byte[] get() {
        if (byteBuf == null) {
            return EMPTY_BUFFER.array();
        }
        byte[] array = new byte[byteBuf.readableBytes()];
        byteBuf.getBytes(byteBuf.readerIndex(), array);
        return array;
    }

    @Override
    public String getString() {
        return getString(HttpConstants.DEFAULT_CHARSET);
    }

    @Override
    public String getString(Charset encoding) {
        if (byteBuf == null) {
            return "";
        }
        if (encoding == null) {
            encoding = HttpConstants.DEFAULT_CHARSET;
        }
        return byteBuf.toString(encoding);
    }

    /**
     * Utility to go from a In Memory FileUpload
     * to a Disk (or another implementation) FileUpload
     * @return the attached ByteBuf containing the actual bytes
     */
    @Override
    public ByteBuf getByteBuf() {
        return byteBuf;
    }

    @Override
    public ByteBuf getChunk(int length) throws IOException {
        if (byteBuf == null || length == 0 || byteBuf.readableBytes() == 0) {
            chunkPosition = 0;
            return EMPTY_BUFFER;
        }
        int sizeLeft = byteBuf.readableBytes() - chunkPosition;
        if (sizeLeft == 0) {
            chunkPosition = 0;
            return EMPTY_BUFFER;
        }
        int sliceLength = length;
        if (sizeLeft < length) {
            sliceLength = sizeLeft;
        }
        ByteBuf chunk = byteBuf.retainedSlice(chunkPosition, sliceLength);
        chunkPosition += sliceLength;
        return chunk;
    }

    @Override
    public boolean isInMemory() {
        return true;
    }

    @Override
    public boolean renameTo(File dest) throws IOException {
        ObjectUtil.checkNotNull(dest, "dest");
        if (byteBuf == null) {
            // empty file
            if (!dest.createNewFile()) {
                throw new IOException("file exists already: " + dest);
            }
            return true;
        }
        int length = byteBuf.readableBytes();
        long written = 0;
        RandomAccessFile accessFile = new RandomAccessFile(dest, "rw");
        try {
            FileChannel fileChannel = accessFile.getChannel();
            try {
                if (byteBuf.nioBufferCount() == 1) {
                    ByteBuffer byteBuffer = byteBuf.nioBuffer();
                    while (written < length) {
                        written += fileChannel.write(byteBuffer);
                    }
                } else {
                    ByteBuffer[] byteBuffers = byteBuf.nioBuffers();
                    while (written < length) {
                        written += fileChannel.write(byteBuffers);
                    }
                }
                fileChannel.force(false);
            } finally {
                fileChannel.close();
            }
        } finally {
            accessFile.close();
        }
        return written == length;
    }

    @Override
    public File getFile() throws IOException {
        throw new IOException("Not represented by a file");
    }

    @Override
    public HttpData touch() {
        return touch(null);
    }

    @Override
    public HttpData touch(Object hint) {
        if (byteBuf != null) {
            byteBuf.touch(hint);
        }
        return this;
    }
}
