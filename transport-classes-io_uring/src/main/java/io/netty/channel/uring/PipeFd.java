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
