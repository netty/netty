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
import io.netty.handler.codec.http.HttpConstants;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;

import static io.netty.util.internal.PlatformDependent.throwException;
import static java.util.Objects.requireNonNull;

/**
 * Abstract Disk HttpData implementation
 */
public abstract class AbstractDiskHttpData<R extends AbstractDiskHttpData<R>> extends AbstractHttpData<R> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractDiskHttpData.class);
    private final BufferAllocator allocator;

    private File file;
    private boolean isRenamed;
    private FileChannel fileChannel;

    protected AbstractDiskHttpData(BufferAllocator allocator, String name, Charset charset, long size) {
        super(name, charset, size);
        this.allocator = requireNonNull(allocator, "allocator");
    }

    /**
     *
     * @return the real DiskFilename (basename)
     */
    protected abstract String getDiskFilename();
    /**
     *
     * @return the default prefix
     */
    protected abstract String getPrefix();
    /**
     *
     * @return the default base Directory
     */
    protected abstract String getBaseDirectory();
    /**
     *
     * @return the default postfix
     */
    protected abstract String getPostfix();
    /**
     *
     * @return True if the file should be deleted on Exit by default
     */
    protected abstract boolean deleteOnExit();

    /**
     * @return a new Temp File from getDiskFilename(), default prefix, postfix and baseDirectory
     */
    private File tempFile() throws IOException {
        String newpostfix;
        String diskFilename = getDiskFilename();
        if (diskFilename != null) {
            newpostfix = '_' + diskFilename;
        } else {
            newpostfix = getPostfix();
        }
        File tmpFile;
        if (getBaseDirectory() == null) {
            // create a temporary file
            tmpFile = PlatformDependent.createTempFile(getPrefix(), newpostfix, null);
        } else {
            tmpFile = PlatformDependent.createTempFile(getPrefix(), newpostfix, new File(
                    getBaseDirectory()));
        }
        if (deleteOnExit()) {
            // See https://github.com/netty/netty/issues/10351
            DeleteFileOnExitHook.add(tmpFile.getPath());
        }
        return tmpFile;
    }

    @Override
    public void setContent(Buffer buffer) throws IOException {
        requireNonNull(buffer, "buffer");
        try (buffer) {
            size = buffer.readableBytes();
            checkSize(size);
            if (definedSize > 0 && definedSize < size) {
                throw new IOException("Out of size: " + size + " > " + definedSize);
            }
            if (file == null) {
                file = tempFile();
            }
            if (buffer.readableBytes() == 0) {
                // empty file
                if (!file.createNewFile()) {
                    if (file.length() == 0) {
                        return;
                    } else {
                        if (!file.delete() || !file.createNewFile()) {
                            throw new IOException("file exists already: " + file);
                        }
                    }
                }
                return;
            }
            // Release the buffer as it was retained before and we not need a reference to it at all
            // See https://github.com/netty/netty/issues/1516
            try (RandomAccessFile accessFile = new RandomAccessFile(file, "rw")) {
                accessFile.setLength(0);
                FileChannel localfileChannel = accessFile.getChannel();
                buffer.forEachReadable(0, (componentIdx, component) -> {
                    final ByteBuffer buf = component.readableBuffer();
                    while (buf.hasRemaining()) {
                        buffer.skipReadable(localfileChannel.write(buf));
                    }
                    return true;
                });
                localfileChannel.force(false);
            }
            setCompleted();
        }
    }

    @Override
    public void addContent(Buffer buffer, boolean last)
            throws IOException {
        if (buffer != null) {
            // Release the buffer as it was retained before and we not need a reference to it at all
            // See https://github.com/netty/netty/issues/1516
            try (buffer) {
                final int localsize = buffer.readableBytes();
                checkSize(size + localsize);
                if (definedSize > 0 && definedSize < size + localsize) {
                    throw new IOException("Out of size: " + (size + localsize) +
                            " > " + definedSize);
                }
                if (file == null) {
                    file = tempFile();
                }
                if (fileChannel == null) {
                    RandomAccessFile accessFile = new RandomAccessFile(file, "rw");
                    fileChannel = accessFile.getChannel();
                }
                buffer.forEachReadable(0, (componentIdx, component) -> {
                    final long position = fileChannel.position();
                    final ByteBuffer buf = component.readableBuffer();
                    int written = 0;
                    while (buf.hasRemaining()) {
                        written += fileChannel.write(buf);
                    }
                    buffer.skipReadable(written);
                    fileChannel.position(position + written);
                    size -= written;
                    return true;
                });
            }
        }
        if (last) {
            if (file == null) {
                file = tempFile();
            }
            if (fileChannel == null) {
                RandomAccessFile accessFile = new RandomAccessFile(file, "rw");
                fileChannel = accessFile.getChannel();
            }
            try {
                fileChannel.force(false);
            } finally {
                fileChannel.close();
            }
            fileChannel = null;
            setCompleted();
        } else {
            requireNonNull(buffer, "buffer");
        }
    }

    @Override
    public void setContent(File file) throws IOException {
        long size = file.length();
        checkSize(size);
        this.size = size;
        if (this.file != null) {
            delete();
        }
        this.file = file;
        isRenamed = true;
        setCompleted();
    }

    @Override
    public void setContent(InputStream inputStream) throws IOException {
        requireNonNull(inputStream, "inputStream");
        if (file != null) {
            delete();
        }
        file = tempFile();
        int written = 0;
        try (RandomAccessFile accessFile = new RandomAccessFile(file, "rw")) {
            accessFile.setLength(0);
            FileChannel localfileChannel = accessFile.getChannel();
            byte[] bytes = new byte[4096 * 4];
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            int read = inputStream.read(bytes);
            while (read > 0) {
                byteBuffer.position(read).flip();
                written += localfileChannel.write(byteBuffer);
                checkSize(written);
                read = inputStream.read(bytes);
            }
            localfileChannel.force(false);
        }
        size = written;
        if (definedSize > 0 && definedSize < size) {
            if (!file.delete()) {
                logger.warn("Failed to delete: {}", file);
            }
            file = null;
            throw new IOException("Out of size: " + size + " > " + definedSize);
        }
        isRenamed = true;
        setCompleted();
    }

    @Override
    public void delete() {
        if (fileChannel != null) {
            try {
                fileChannel.force(false);
            } catch (IOException e) {
                logger.warn("Failed to force.", e);
            } finally {
                try {
                    fileChannel.close();
                } catch (IOException e) {
                    logger.warn("Failed to close a file.", e);
                }
            }
            fileChannel = null;
        }
        if (!isRenamed) {
            String filePath = null;

            if (file != null && file.exists()) {
                filePath = file.getPath();
                if (!file.delete()) {
                    filePath = null;
                    logger.warn("Failed to delete: {}", file);
                }
            }

            // If you turn on deleteOnExit make sure it is executed.
            if (deleteOnExit() && filePath != null) {
                DeleteFileOnExitHook.remove(filePath);
            }
            file = null;
        }
    }

    @Override
    public byte[] get() throws IOException {
        if (file == null) {
            return EmptyArrays.EMPTY_BYTES;
        }
        return readFrom(file);
    }

    @Override
    public Buffer getBuffer() throws IOException {
        if (file == null) {
            return allocator.allocate(0);
        }
        byte[] array = readFrom(file);
        // TODO: This may be optimized to reduce memory copies
        return allocator.allocate(array.length).writeBytes(array);
    }

    @Override
    public Buffer getChunk(int length) throws IOException {
        if (file == null || length == 0) {
            return allocator.allocate(0);
        }
        if (fileChannel == null) {
            RandomAccessFile accessFile = new RandomAccessFile(file, "r");
            fileChannel = accessFile.getChannel();
        }
        int read = 0;
        ByteBuffer byteBuffer = ByteBuffer.allocate(length);
        try {
            while (read < length) {
                int readnow = fileChannel.read(byteBuffer);
                if (readnow == -1) {
                    fileChannel.close();
                    fileChannel = null;
                    break;
                }
                read += readnow;
            }
        } catch (IOException e) {
            fileChannel.close();
            fileChannel = null;
            throw e;
        }
        if (read == 0) {
            return allocator.allocate(0);
        }
        byteBuffer.flip();
        final Buffer buffer = allocator.allocate(byteBuffer.remaining());
        while (byteBuffer.hasRemaining()) {
            buffer.writeByte(byteBuffer.get());
        }
        return buffer;
    }

    @Override
    public String getString() throws IOException {
        return getString(HttpConstants.DEFAULT_CHARSET);
    }

    @Override
    public String getString(Charset encoding) throws IOException {
        if (file == null) {
            return "";
        }
        if (encoding == null) {
            byte[] array = readFrom(file);
            return new String(array, HttpConstants.DEFAULT_CHARSET.name());
        }
        byte[] array = readFrom(file);
        return new String(array, encoding.name());
    }

    @Override
    public boolean isInMemory() {
        return false;
    }

    @Override
    public boolean renameTo(File dest) throws IOException {
        requireNonNull(dest, "dest");
        if (file == null) {
            throw new IOException("No file defined so cannot be renamed");
        }
        if (!file.renameTo(dest)) {
            // must copy
            IOException exception = null;
            RandomAccessFile inputAccessFile = null;
            RandomAccessFile outputAccessFile = null;
            long chunkSize = 8196;
            long position = 0;
            try {
                inputAccessFile = new RandomAccessFile(file, "r");
                outputAccessFile = new RandomAccessFile(dest, "rw");
                FileChannel in = inputAccessFile.getChannel();
                FileChannel out = outputAccessFile.getChannel();
                while (position < size) {
                    if (chunkSize < size - position) {
                        chunkSize = size - position;
                    }
                    position += in.transferTo(position, chunkSize, out);
                }
            } catch (IOException e) {
                exception = e;
            } finally {
                if (inputAccessFile != null) {
                    try {
                        inputAccessFile.close();
                    } catch (IOException e) {
                        if (exception == null) { // Choose to report the first exception
                            exception = e;
                        } else {
                            logger.warn("Multiple exceptions detected, the following will be suppressed {}", e);
                        }
                    }
                }
                if (outputAccessFile != null) {
                    try {
                        outputAccessFile.close();
                    } catch (IOException e) {
                        if (exception == null) { // Choose to report the first exception
                            exception = e;
                        } else {
                            logger.warn("Multiple exceptions detected, the following will be suppressed {}", e);
                        }
                    }
                }
            }
            if (exception != null) {
                throw exception;
            }
            if (position == size) {
                if (!file.delete()) {
                    logger.warn("Failed to delete: {}", file);
                }
                file = dest;
                isRenamed = true;
                return true;
            } else {
                if (!dest.delete()) {
                    logger.warn("Failed to delete: {}", dest);
                }
                return false;
            }
        }
        file = dest;
        isRenamed = true;
        return true;
    }

    /**
     * Utility function
     *
     * @return the array of bytes
     */
    private static byte[] readFrom(File src) throws IOException {
        return Files.readAllBytes(src.toPath());
    }

    @Override
    public File getFile() throws IOException {
        return file;
    }

    @Override
    public void close() {
        try {
            fileChannel.close();
        } catch (IOException e) {
            throwException(e);
        }
    }

    @Override
    public boolean isAccessible() {
        return fileChannel.isOpen();
    }

    protected BufferAllocator allocator() {
        return allocator;
    }
}
