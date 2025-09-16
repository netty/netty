package io.netty.handler.codec.compression;

import com.github.luben.zstd.ZstdInputStreamNoFinalizer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.io.IOException;
import java.io.InputStream;

public class ZstdDecompressor implements Decompressor {
    private final ByteBufAllocator allocator;

    private final MutableByteBufInputStream mutableInput = new MutableByteBufInputStream();
    private final ZstdInputStreamNoFinalizer output;

    {
        try {
            output = new ZstdInputStreamNoFinalizer(mutableInput);
        } catch (IOException e) {
            throw new DecompressionException(e);
        }
        output.setContinuous(true);
    }

    ZstdDecompressor(Builder builder) {
        this.allocator = builder.allocator;
    }

    @Override
    public Status status() throws DecompressionException {
        try {
            return output.available() == 0 ? Status.NEED_INPUT : Status.NEED_OUTPUT;
        } catch (IOException e) {
            throw new DecompressionException(e);
        }
    }

    @Override
    public void addInput(ByteBuf buf) throws DecompressionException {
        if (mutableInput.current != null) {
            mutableInput.current.release();
        }
        mutableInput.current = buf;
    }

    @Override
    public void endOfInput() throws DecompressionException {
        output.setContinuous(false);
    }

    @Override
    public ByteBuf takeOutput() throws DecompressionException {
        ByteBuf buf = allocator.buffer();
        try {
            buf.writeBytes(output, buf.maxFastWritableBytes());
        } catch (IOException e) {
            buf.release();
            throw new DecompressionException(e);
        }
        return buf;
    }

    @Override
    public void close() {
        if (mutableInput.current != null) {
            mutableInput.current.release();
        }
        try {
            output.close();
        } catch (IOException ignored) {
        }
    }

    public static Builder builder(ByteBufAllocator allocator) {
        return new Builder(allocator);
    }

    public static final class Builder extends AbstractDecompressorBuilder {
        Builder(ByteBufAllocator allocator) {
            super(allocator);
        }

        @Override
        public Decompressor build() throws DecompressionException {
            return new DefensiveDecompressor(new ZstdDecompressor(this));
        }
    }

    private static final class MutableByteBufInputStream extends InputStream {
        ByteBuf current;

        @Override
        public int read() {
            if (available() == 0) {
                return -1;
            }
            return current.readByte() & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            int available = available();
            if (available == 0) {
                return -1;
            }

            len = Math.min(available, len);
            current.readBytes(b, off, len);
            return len;
        }

        @Override
        public int available() {
            return current == null ? 0 : current.readableBytes();
        }
    }
}
