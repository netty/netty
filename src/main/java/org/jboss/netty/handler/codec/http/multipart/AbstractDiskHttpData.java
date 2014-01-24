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
package org.jboss.netty.handler.codec.http.multipart;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.HttpConstants;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

/**
 * Abstract Disk HttpData implementation
 */
public abstract class AbstractDiskHttpData extends AbstractHttpData {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractDiskHttpData.class);

    protected File file;
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

    public void setContent(ChannelBuffer buffer) throws IOException {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
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
            file.createNewFile();
            return;
        }
        FileOutputStream outputStream = new FileOutputStream(file);
        FileChannel localfileChannel = outputStream.getChannel();
        ByteBuffer byteBuffer = buffer.toByteBuffer();
        int written = 0;
        while (written < size) {
            written += localfileChannel.write(byteBuffer);
        }
        buffer.readerIndex(buffer.readerIndex() + written);
        localfileChannel.force(false);
        localfileChannel.close();
        outputStream.close();
        completed = true;
    }

    public void addContent(ChannelBuffer buffer, boolean last)
            throws IOException {
        if (buffer != null) {
            int localsize = buffer.readableBytes();
            checkSize(size + localsize);
            if (definedSize > 0 && definedSize < size + localsize) {
                throw new IOException("Out of size: " + (size + localsize) +
                        " > " + definedSize);
            }
            ByteBuffer byteBuffer = buffer.toByteBuffer();
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
            completed = true;
        } else {
            if (buffer == null) {
                throw new NullPointerException("buffer");
            }
        }
    }

    public void setContent(File file) throws IOException {
        if (this.file != null) {
            delete();
        }
        this.file = file;
        size = file.length();
        checkSize(size);
        isRenamed = true;
        completed = true;
    }

    public void setContent(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new NullPointerException("inputStream");
        }
        if (file != null) {
            delete();
        }
        file = tempFile();
        FileOutputStream outputStream = new FileOutputStream(file);
        FileChannel localfileChannel = outputStream.getChannel();
        byte[] bytes = new byte[4096 * 4];
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        int read = inputStream.read(bytes);
        int written = 0;
        while (read > 0) {
            byteBuffer.position(read).flip();
            written += localfileChannel.write(byteBuffer);
            checkSize(written);
            read = inputStream.read(bytes);
        }
        localfileChannel.force(false);
        localfileChannel.close();
        size = written;
        if (definedSize > 0 && definedSize < size) {
            file.delete();
            file = null;
            throw new IOException("Out of size: " + size + " > " + definedSize);
        }
        isRenamed = true;
        completed = true;
    }

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
                file.delete();
            }
            file = null;
        }
    }

    public byte[] get() throws IOException {
        if (file == null) {
            return new byte[0];
        }
        return readFrom(file);
    }

    public ChannelBuffer getChannelBuffer() throws IOException {
        if (file == null) {
            return ChannelBuffers.EMPTY_BUFFER;
        }
        byte[] array = readFrom(file);
        return ChannelBuffers.wrappedBuffer(array);
    }

    public ChannelBuffer getChunk(int length) throws IOException {
        if (file == null || length == 0) {
            return ChannelBuffers.EMPTY_BUFFER;
        }
        if (fileChannel == null) {
            FileInputStream  inputStream = new FileInputStream(file);
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
            return ChannelBuffers.EMPTY_BUFFER;
        }
        byteBuffer.flip();
        ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(byteBuffer);
        buffer.readerIndex(0);
        buffer.writerIndex(read);
        return buffer;
    }

    public String getString() throws IOException {
        return getString(HttpConstants.DEFAULT_CHARSET);
    }

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

    public boolean isInMemory() {
        return false;
    }

    public boolean renameTo(File dest) throws IOException {
        if (dest == null) {
            throw new NullPointerException("dest");
        }
        if (file == null) {
            throw new IOException("No file defined so cannot be renamed");
        }
        if (!file.renameTo(dest)) {
            // must copy
            FileInputStream inputStream = new FileInputStream(file);
            FileOutputStream outputStream = new FileOutputStream(dest);
            FileChannel in = inputStream.getChannel();
            FileChannel out = outputStream.getChannel();
            int chunkSize = 8196;
            long position = 0;
            while (position < size) {
                if (chunkSize < size - position) {
                    chunkSize = (int) (size - position);
                }
                position += in.transferTo(position, chunkSize , out);
            }
            in.close();
            out.close();
            if (position == size) {
                file.delete();
                file = dest;
                isRenamed = true;
                return true;
            } else {
                dest.delete();
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
        FileChannel fileChannel = inputStream.getChannel();
        byte[] array = new byte[(int) srcsize];
        ByteBuffer byteBuffer = ByteBuffer.wrap(array);
        int read = 0;
        while (read < srcsize) {
            read += fileChannel.read(byteBuffer);
        }
        fileChannel.close();
        return array;
    }

    public File getFile() throws IOException {
        return file;
    }

}
