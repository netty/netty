package org.jboss.netty.handler.stream;

import java.io.IOException;
import java.io.OutputStream;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;

/**
 * {@link OutputStream} which write data to the wrapped {@link Channel}
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://www.murkycloud.com/">Norman Maurer</a>
 */
public class ChannelOutputStream extends OutputStream{

    private final Channel channel;

    private ChannelFuture lastChannelFuture;

    public ChannelOutputStream(Channel channel) {
        this.channel = channel;
    }

    @Override
    public void close() throws IOException {
        try {
            flush();
        } finally {
            channel.close().awaitUninterruptibly();
        }
    }

    private void checkClosed() throws IOException {
        if (!channel.isConnected()) {
            throw new IOException("The session has been closed.");
        }
    }

    private synchronized void write(ChannelBuffer buf) throws IOException {
        checkClosed();
        ChannelFuture future = channel.write(buf);
        lastChannelFuture = future;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        write(ChannelBuffers.copiedBuffer(b.clone(), off, len));
    }

    @Override
    public void write(int b) throws IOException {
        ChannelBuffer buf = ChannelBuffers.buffer(1);
        buf.writeByte((byte) b);
        write(buf);
    }

    @Override
    public synchronized void flush() throws IOException {
        if (lastChannelFuture == null) {
            return;
        }

        lastChannelFuture.awaitUninterruptibly();
        if (!lastChannelFuture.isSuccess()) {
            Throwable t = lastChannelFuture.getCause();
            if (t != null) {
                throw new IOException(
                        "The bytes could not be written to the session", t);
            } else {
                throw new IOException(
                        "The bytes could not be written to the session");
            }

        }
    }

}
