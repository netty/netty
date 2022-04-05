/*
 * Copyright 2022 The Netty Project
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
package io.netty5.handler.ssl;

import io.netty.buffer.ByteBuf;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.ReadableComponent;
import io.netty5.buffer.api.ReadableComponentProcessor;
import io.netty5.buffer.api.WritableComponent;
import io.netty5.buffer.api.WritableComponentProcessor;
import io.netty5.buffer.api.internal.Statics;
import io.netty5.util.internal.PlatformDependent;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Used by {@link SslHandler} to {@linkplain SSLEngine#wrap(ByteBuffer, ByteBuffer) wrap} and
 * {@linkplain SSLEngine#unwrap(ByteBuffer, ByteBuffer) unwrap} {@link Buffer} instances, which requires them to be
 * projected into {@link ByteBuffer}s.
 * <p>
 * Instances of this class are single-threaded, and each {@link SslHandler} will have their own instance.
 */
class EngineWrapper implements ReadableComponentProcessor<RuntimeException>,
                               WritableComponentProcessor<RuntimeException> {
    /**
     * Used for handshakes with engines that require off-heap buffers.
     */
    private static final ByteBuffer EMPTY_BUFFER_DIRECT = ByteBuffer.allocateDirect(0);
    /**
     * Used for handshakes with engines that require on-heap buffers.
     */
    private static final ByteBuffer EMPTY_BUFFER_HEAP = ByteBuffer.allocate(0);

    private final SSLEngine engine;
    private final boolean useDirectBuffer;

    /**
     * Used if {@link SSLEngine#wrap(ByteBuffer[], ByteBuffer)} and {@link SSLEngine#unwrap(ByteBuffer, ByteBuffer[])}
     * should be called with a {@link ByteBuf} that is only backed by one {@link ByteBuffer} to reduce the object
     * creation.
     */
    private final ByteBuffer[] singleEmptyBuffer;
    private final ByteBuffer[] singleReadableBuffer;
    private final ByteBuffer[] singleWritableBuffer;

    private ByteBuffer[] inputs;
    private ByteBuffer[] outputs;
    private SSLEngineResult result;
    private ByteBuffer cachedReadingBuffer;
    private ByteBuffer cachedWritingBuffer;
    private boolean writeBack;

    EngineWrapper(SSLEngine engine, boolean useDirectBuffer) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.useDirectBuffer = useDirectBuffer;
        singleEmptyBuffer = new ByteBuffer[1];
        singleEmptyBuffer[0] = useDirectBuffer? EMPTY_BUFFER_DIRECT : EMPTY_BUFFER_HEAP;
        singleReadableBuffer = new ByteBuffer[1];
        singleWritableBuffer = new ByteBuffer[1];
    }

    SSLEngineResult wrap(Buffer in, Buffer out) throws SSLException {
        try {
            prepare(in, out);
            int count = outputs.length;
            assert count == 1 : "Wrap can only output to a single buffer, but got " + count + " buffers.";
            return processResult(engine.wrap(inputs, outputs[0]));
        } finally {
            finish(in, out);
        }
    }

    SSLEngineResult unwrap(Buffer in, int length, Buffer out) throws SSLException {
        try {
            prepare(in, out);
            limitInput(length);
            if (engine instanceof VectoredUnwrap) {
                VectoredUnwrap vectoredEngine = (VectoredUnwrap) engine;
                return processResult(vectoredEngine.unwrap(inputs, outputs));
            }
            if (inputs.length > 1) {
                coalesceInputs();
            }
            return processResult(engine.unwrap(inputs[0], outputs));
        } finally {
            finish(in, out);
        }
    }

    private void prepare(Buffer in, Buffer out) {
        if (in == null || in.readableBytes() == 0) {
            inputs = singleEmptyBuffer;
        } else if (in.isDirect() == useDirectBuffer) {
            int count = in.countReadableComponents();
            assert count > 0 : "Input buffer has readable bytes, but no readable components: " + in;
            inputs = count == 1? singleReadableBuffer : new ByteBuffer[count];
            int prepared = in.forEachReadable(0, this);
            assert prepared == count : "Expected to prepare " + count + " buffers, but got " + prepared;
        } else {
            inputs = singleReadableBuffer;
            int readable = in.readableBytes();
            if (cachedReadingBuffer == null || cachedReadingBuffer.capacity() < readable) {
                cachedReadingBuffer = allocateCachingBuffer(readable);
            }
            cachedReadingBuffer.clear();
            in.copyInto(in.readerOffset(), cachedReadingBuffer, 0, readable);
            cachedReadingBuffer.limit(readable);
            inputs[0] = cachedReadingBuffer;
        }
        if (out == null || out.writableBytes() == 0) {
            outputs = singleEmptyBuffer;
        } else if (out.isDirect() == useDirectBuffer) {
            int count = out.countWritableComponents();
            assert count > 0 : "Output buffer has writable space, but no writable components: " + out;
            outputs = count == 1? singleWritableBuffer : new ByteBuffer[count];
            int prepared = out.forEachWritable(0, this);
            assert prepared == count : "Expected to prepare " + count + " buffers, but got " + prepared;
        } else {
            inputs = singleWritableBuffer;
            int writable = out.writableBytes();
            if (cachedWritingBuffer == null || cachedWritingBuffer.capacity() < writable) {
                cachedWritingBuffer = allocateCachingBuffer(writable);
            }
            outputs[0] = cachedWritingBuffer.position(0).limit(writable);
            writeBack = true;
        }
    }

    private ByteBuffer allocateCachingBuffer(int capacity) {
        capacity = PlatformDependent.roundToPowerOfTwo(capacity);
        return useDirectBuffer? ByteBuffer.allocateDirect(capacity) : ByteBuffer.allocate(capacity);
    }

    private void limitInput(int length) {
        for (ByteBuffer input : inputs) {
            int remaining = input.remaining();
            if (remaining > length) {
                input.limit(input.position() + length);
                length = 0;
            } else {
                length -= remaining;
            }
        }
    }

    private void coalesceInputs() {
        int rem = 0;
        for (ByteBuffer input : inputs) {
            rem += input.remaining();
        }
        if (cachedReadingBuffer == null || cachedReadingBuffer.capacity() < rem) {
            cachedReadingBuffer = allocateCachingBuffer(rem);
        }
        cachedReadingBuffer.clear();
        for (ByteBuffer input : inputs) {
            cachedReadingBuffer.put(input);
        }
        cachedReadingBuffer.flip();
        singleReadableBuffer[0] = cachedReadingBuffer;
        inputs = singleReadableBuffer;
    }

    private SSLEngineResult processResult(SSLEngineResult result) {
        this.result = result;
        return result;
    }

    private void finish(Buffer in, Buffer out) {
        if (result != null) { // Result can be null if the operation failed.
            if (in != null) {
                in.skipReadable(result.bytesConsumed());
            }
            if (out != null) {
                if (writeBack) {
                    assert outputs.length == 1;
                    ByteBuffer buf = outputs[0];
                    while (buf.remaining() >= Long.BYTES) {
                        out.writeLong(buf.getLong());
                    }
                    if (buf.remaining() >= Integer.BYTES) {
                        out.writeInt(buf.getInt());
                    }
                    if (buf.remaining() >= Short.BYTES) {
                        out.writeShort(buf.getShort());
                    }
                    if (buf.hasRemaining()) {
                        out.writeByte(buf.get());
                    }
                } else {
                    out.skipWritable(result.bytesProduced());
                }
            }
            result = null;
        }
        singleReadableBuffer[0] = null;
        singleWritableBuffer[0] = null;
        inputs = null;
        outputs = null;
        // The ByteBuffers used for wrap/unwrap may be derived from the internal memory of the in/out Buffer instances.
        // Use a reachability fence to guarantee that the memory is not freed via a GC route before we've removed all
        // references to the ByteBuffers using that memory.
        // First, we null-out the ByteBuffer references (above), then we fence the Buffer instances (below).
        Reference.reachabilityFence(in);
        Reference.reachabilityFence(out);
    }

    @Override
    public boolean process(int index, ReadableComponent component) {
        // Some SSLEngine implementations require their input buffers be mutable. Let's try to accommodate.
        // See https://bugs.openjdk.java.net/browse/JDK-8283577 for the details.
        ByteBuffer byteBuffer = Statics.tryGetWritableBufferFromReadableComponent(component);
        if (byteBuffer == null) {
            byteBuffer = component.readableBuffer();
        }
        inputs[index] = byteBuffer;
        return true;
    }

    @Override
    public boolean process(int index, WritableComponent component) {
        outputs[index] = component.writableBuffer();
        return true;
    }

    @Override
    public String toString() {
        return "EngineWrapper(for " + engine.getPeerPort() + ')';
    }
}
