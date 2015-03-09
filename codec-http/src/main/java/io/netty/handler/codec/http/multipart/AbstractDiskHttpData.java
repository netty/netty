/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.http.multipart;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

import static io.netty.buffer.Unpooled.*;

/**
 * Abstract Disk HttpData implementation
 */
public abstract class AbstractDiskHttpData extends AbstractHttpData {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractDiskHttpData.class);

    private File file;
    private boolean isRenamed;
    private FileChannel fileChannel;

    protected AbstractDiskHttpData(String name, Charset charset, long size) {
        super(name, charset, size);
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
            tmpFile = File.createTempFile(getPrefix(), newpostfix);
        } else {
            tmpFile = File.createTempFile(getPrefix(), newpostfix, new File(
                    getBaseDirectory()));
        }
        if (deleteOnExit()) {
            tmpFile.deleteOnExit();
        }
        return tmpFile;
    }

    @Override
    public void setContent(ByteBuf buffer) throws IOException {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        try {
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
                    throw new IOException("file exists already: " + file);
                }
                return;
            }
            FileOutputStream outputStream = new FileOutputStream(file);
            try {
                FileChannel localfileChannel = outputStream.getChannel();
                ByteBuffer byteBuffer = buffer.nioBuffer();
                int written = 0;
                while (written < size) {
                    written += localfileChannel.write(byteBuffer);
                }
                buffer.readerIndex(buffer.readerIndex() + written);
                localfileChannel.force(false);
            } finally {
                outputStream.close();
            }
            setCompleted();
        } finally {
            // Release the buffer as it was retained before and we not need a reference to it at all
            // See https://github.com/netty/netty/issues/1516
            buffer.release();
        }
    }

    @Override
    public void addContent(ByteBuf buffer, boolean last)
            throws IOException {
        if (buffer != null) {
            try {
                int localsize = buffer.readableBytes();
                checkSize(size + localsize);
                if (definedSize > 0 && definedSize < size + localsize) {
                    throw new IOException("Out of size: " + (size + localsize) +
                            " > " + definedSize);
                }
                ByteBuffer byteBuffer = buffer.nioBufferCount() == 1 ? buffer.nioBuffer() : buffer.copy().nioBuffer();
                int written = 0;
                if (file == null) {
                    file = tempFile();
                }
                if (fileChannel == null) {
                    FileOutputStream outputStream = new FileOutputStream(file);
                    fileChannel = outputStream.getChannel();
                }
                while (written < localsize) {
                    written += fileChannel.write(byteBuffer);
                }
                size += localsize;
                buffer.readerIndex(buffer.readerIndex() + written);
            } finally {
                // Release the buffer as it was retained before and we not need a reference to it at all
                // See https://github.com/netty/netty/issues/1516
                buffer.release();
            }
        }
        if (last) {
            if (file == null) {
                file = tempFile();
            }
            if (fileChannel == null) {
                FileOutputStream outputStream = new FileOutputStream(file);
                fileChannel = outputStream.getChannel();
            }
            fileChannel.force(false);
            fileChannel.close();
            fileChannel = null;
            setCompleted();
        } else {
            if (buffer == null) {
                throw new NullPointerException("buffer");
            }
        }
    }

    @Override
    public void setContent(File file) throws IOException {
        if (this.file != null) {
            delete();
        }
        this.file = file;
        size = file.length();
        checkSize(size);
        isRenamed = true;
        setCompleted();
    }

    @Override
    public void setContent(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new NullPointerException("inputStream");
        }
        if (file != null) {
            delete();
        }
        file = tempFile();
        FileOutputStream outputStream = new FileOutputStream(file);
        int written = 0;
        try {
            FileChannel localfileChannel = outputStream.getChannel();
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
        } finally {
            outputStream.close();
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
                fileChannel.close();
            } catch (IOException e) {
                logger.warn("Failed to close a file.", e);
            }
            fileChannel = null;
        }
        if (! isRenamed) {
            if (file != null && file.exists()) {
                if (!file.delete()) {
                    logger.warn("Failed to delete: {}", file);
                }
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
    public ByteBuf getByteBuf() throws IOException {
        if (file == null) {
            return EMPTY_BUFFER;
        }
        byte[] array = readFrom(file);
        return wrappedBuffer(array);
    }

    @Override
    public ByteBuf getChunk(int length) throws IOException {
        if (file == null || length == 0) {
            return EMPTY_BUFFER;
        }
        if (fileChannel == null) {
            FileInputStream inputStream = new FileInputStream(file);
            fileChannel = inputStream.getChannel();
        }
        int read = 0;
        ByteBuffer byteBuffer = ByteBuffer.allocate(length);
        while (read < length) {
            int readnow = fileChannel.read(byteBuffer);
            if (readnow == -1) {
                fileChannel.close();
                fileChannel = null;
                break;
            } else {
                read += readnow;
            }
        }
        if (read == 0) {
            return EMPTY_BUFFER;
        }
        byteBuffer.flip();
        ByteBuf buffer = wrappedBuffer(byteBuffer);
        buffer.readerIndex(0);
        buffer.writerIndex(read);
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
        if (dest == null) {
            throw new NullPointerException("dest");
        }
        if (file == null) {
            throw new IOException("No file defined so cannot be renamed");
        }
        if (!file.renameTo(dest)) {
            // must copy
            IOException exception = null;
            FileInputStream inputStream = null;
            FileOutputStream outputStream = null;
            long chunkSize = 8196;
            long position = 0;
            try {
                inputStream = new FileInputStream(file);
                outputStream = new FileOutputStream(dest);
                FileChannel in = inputStream.getChannel();
                FileChannel out = outputStream.getChannel();
                while (position < size) {
                    if (chunkSize < size - position) {
                        chunkSize = size - position;
                    }
                    position += in.transferTo(position, chunkSize , out);
                }
            } catch (IOException e) {
                exception = e;
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        if (exception == null) { // Choose to report the first exception
                            exception = e;
                        } else {
                            logger.warn("Multiple exceptions detected, the following will be suppressed {}", e);
                        }
                    }
                }
                if (outputStream != null) {
                    try {
                        outputStream.close();
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
     * @return the array of bytes
     */
    private static byte[] readFrom(File src) throws IOException {
        long srcsize = src.length();
        if (srcsize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "File too big to be loaded in memory");
        }
        FileInputStream inputStream = new FileInputStream(src);
        byte[] array = new byte[(int) srcsize];
        try {
            FileChannel fileChannel = inputStream.getChannel();
            ByteBuffer byteBuffer = ByteBuffer.wrap(array);
            int read = 0;
            while (read < srcsize) {
                read += fileChannel.read(byteBuffer);
            }
        } finally {
            inputStream.close();
        }
        return array;
    }

    @Override
    public File getFile() throws IOException {
        return file;
    }

    @Override
    public HttpData touch() {
        return this;
    }

    @Override
    public HttpData touch(Object hint) {
        return this;
    }
}
