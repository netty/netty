/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.buffer;

import static org.jboss.netty.buffer.ChannelBuffers.*;
import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author Frederic Bregier (fredbregier@free.fr)
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public abstract class AbstractCompositeChannelBufferTest extends
        AbstractChannelBufferTest {

    private final ByteOrder order;

    protected AbstractCompositeChannelBufferTest(ByteOrder order) {
        if (order == null) {
            throw new NullPointerException("order");
        }
        this.order = order;
    }

    private List<ChannelBuffer> buffers;
    private ChannelBuffer buffer;

    @Override
    protected ChannelBuffer newBuffer(int length) {
        buffers = new ArrayList<ChannelBuffer>();
        for (int i = 0; i < length; i += 10) {
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(order, new byte[1]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(order, new byte[2]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(order, new byte[3]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(order, new byte[4]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(order, new byte[5]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(order, new byte[6]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(order, new byte[7]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(order, new byte[8]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(order, new byte[9]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
        }

        buffer = ChannelBuffers.wrappedBuffer(buffers.toArray(new ChannelBuffer[buffers.size()]));
        buffer.writerIndex(length);
        buffer = ChannelBuffers.wrappedBuffer(buffer);
        assertEquals(length, buffer.capacity());
        assertEquals(length, buffer.readableBytes());
        assertFalse(buffer.writable());
        buffer.writerIndex(0);
        return buffer;
    }

    @Override
    protected ChannelBuffer[] components() {
        return buffers.toArray(new ChannelBuffer[buffers.size()]);
    }

    // Composite buffer does not waste bandwidth on discardReadBytes, but
    // the test will fail in strict mode.
    @Override
    protected boolean discardReadBytesDoesNotMoveWritableBytes() {
        return false;
    }

    @Test
    public void testDiscardReadBytes3() {
        ChannelBuffer a, b;
        a = wrappedBuffer(order, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
        b = wrappedBuffer(
                wrappedBuffer(order, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }, 0, 5),
                wrappedBuffer(order, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }, 5, 5));
        a.skipBytes(6);
        a.markReaderIndex();
        b.skipBytes(6);
        b.markReaderIndex();
        assertEquals(a.readerIndex(), b.readerIndex());
        a.readerIndex(a.readerIndex()-1);
        b.readerIndex(b.readerIndex()-1);
        assertEquals(a.readerIndex(), b.readerIndex());
        a.writerIndex(a.writerIndex()-1);
        a.markWriterIndex();
        b.writerIndex(b.writerIndex()-1);
        b.markWriterIndex();
        assertEquals(a.writerIndex(), b.writerIndex());
        a.writerIndex(a.writerIndex()+1);
        b.writerIndex(b.writerIndex()+1);
        assertEquals(a.writerIndex(), b.writerIndex());
        assertTrue(ChannelBuffers.equals(a, b));
        // now discard
        a.discardReadBytes();
        b.discardReadBytes();
        assertEquals(a.readerIndex(), b.readerIndex());
        assertEquals(a.writerIndex(), b.writerIndex());
        assertTrue(ChannelBuffers.equals(a, b));
        a.resetReaderIndex();
        b.resetReaderIndex();
        assertEquals(a.readerIndex(), b.readerIndex());
        a.resetWriterIndex();
        b.resetWriterIndex();
        assertEquals(a.writerIndex(), b.writerIndex());
        assertTrue(ChannelBuffers.equals(a, b));
    }

    @Test
    public void testCompositeWrappedBuffer() {
        ChannelBuffer header = dynamicBuffer(order, 12);
        ChannelBuffer payload = dynamicBuffer(order, 512);

        header.writeBytes(new byte[12]);
        payload.writeBytes(new byte[512]);

        ChannelBuffer buffer = wrappedBuffer(header, payload);

        assertTrue(header.readableBytes() == 12);
        assertTrue(payload.readableBytes() == 512);

        assertEquals(12 + 512, buffer.readableBytes());

        assertEquals(12 + 512, buffer.toByteBuffer(0, 12 + 512).remaining());
    }
    @Test
    public void testSeveralBuffersEquals() {
        ChannelBuffer a, b;
        //XXX Same tests with several buffers in wrappedCheckedBuffer
        // Different length.
        a = wrappedBuffer(order, new byte[] { 1  });
        b = wrappedBuffer(wrappedBuffer(order, new byte[] { 1 }),
                wrappedBuffer(order, new byte[] { 2 }));
        assertFalse(ChannelBuffers.equals(a, b));

        // Same content, same firstIndex, short length.
        a = wrappedBuffer(order, new byte[] { 1, 2, 3 });
        b = wrappedBuffer(wrappedBuffer(order, new byte[] { 1 }),
                wrappedBuffer(order, new byte[] { 2 }),
                wrappedBuffer(order, new byte[] { 3 }));
        assertTrue(ChannelBuffers.equals(a, b));

        // Same content, different firstIndex, short length.
        a = wrappedBuffer(order, new byte[] { 1, 2, 3 });
        b = wrappedBuffer(wrappedBuffer(order, new byte[] { 0, 1, 2, 3, 4 }, 1, 2),
                wrappedBuffer(order, new byte[] { 0, 1, 2, 3, 4 }, 3, 1));
        assertTrue(ChannelBuffers.equals(a, b));

        // Different content, same firstIndex, short length.
        a = wrappedBuffer(order, new byte[] { 1, 2, 3 });
        b = wrappedBuffer(wrappedBuffer(order, new byte[] { 1, 2 }),
                wrappedBuffer(order, new byte[] { 4 }));
        assertFalse(ChannelBuffers.equals(a, b));

        // Different content, different firstIndex, short length.
        a = wrappedBuffer(order, new byte[] { 1, 2, 3 });
        b = wrappedBuffer(wrappedBuffer(order, new byte[] { 0, 1, 2, 4, 5 }, 1, 2),
                wrappedBuffer(order, new byte[] { 0, 1, 2, 4, 5 }, 3, 1));
        assertFalse(ChannelBuffers.equals(a, b));

        // Same content, same firstIndex, long length.
        a = wrappedBuffer(order, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
        b = wrappedBuffer(wrappedBuffer(order, new byte[] { 1, 2, 3 }),
                wrappedBuffer(order, new byte[] { 4, 5, 6 }),
                wrappedBuffer(order, new byte[] { 7, 8, 9, 10 }));
        assertTrue(ChannelBuffers.equals(a, b));

        // Same content, different firstIndex, long length.
        a = wrappedBuffer(order, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
        b = wrappedBuffer(wrappedBuffer(order, new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 1, 5),
                wrappedBuffer(order, new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 6, 5));
        assertTrue(ChannelBuffers.equals(a, b));

        // Different content, same firstIndex, long length.
        a = wrappedBuffer(order, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
        b = wrappedBuffer(wrappedBuffer(order, new byte[] { 1, 2, 3, 4, 6 }),
                wrappedBuffer(order, new byte[] { 7, 8, 5, 9, 10 }));
        assertFalse(ChannelBuffers.equals(a, b));

        // Different content, different firstIndex, long length.
        a = wrappedBuffer(order, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
        b = wrappedBuffer(wrappedBuffer(order, new byte[] { 0, 1, 2, 3, 4, 6, 7, 8, 5, 9, 10, 11 }, 1, 5),
                wrappedBuffer(order, new byte[] { 0, 1, 2, 3, 4, 6, 7, 8, 5, 9, 10, 11 }, 6, 5));
        assertFalse(ChannelBuffers.equals(a, b));
    }
    @Test
    public void testWrappedBuffer() {

        assertEquals(16, wrappedBuffer(wrappedBuffer(ByteBuffer.allocateDirect(16))).capacity());

        assertEquals(
                wrappedBuffer(wrappedBuffer(order, new byte[] { 1, 2, 3 })),
                wrappedBuffer(wrappedBuffer(order, new byte[][] { new byte[] { 1, 2, 3 } })));

        assertEquals(
                wrappedBuffer(wrappedBuffer(order, new byte[] { 1, 2, 3 })),
                wrappedBuffer(wrappedBuffer(order,
                        new byte[] { 1 },
                        new byte[] { 2 },
                        new byte[] { 3 })));

        assertEquals(
                wrappedBuffer(wrappedBuffer(order, new byte[] { 1, 2, 3 })),
                wrappedBuffer(new ChannelBuffer[] {
                        wrappedBuffer(order, new byte[] { 1, 2, 3 })
                }));

        assertEquals(
                wrappedBuffer(wrappedBuffer(order, new byte[] { 1, 2, 3 })),
                wrappedBuffer(
                        wrappedBuffer(order, new byte[] { 1 }),
                        wrappedBuffer(order, new byte[] { 2 }),
                        wrappedBuffer(order, new byte[] { 3 })));

        assertEquals(
                wrappedBuffer(wrappedBuffer(order, new byte[] { 1, 2, 3 })),
                wrappedBuffer(wrappedBuffer(new ByteBuffer[] {
                        ByteBuffer.wrap(new byte[] { 1, 2, 3 })
                })));

        assertEquals(
                wrappedBuffer(wrappedBuffer(order, new byte[] { 1, 2, 3 })),
                wrappedBuffer(wrappedBuffer(
                        ByteBuffer.wrap(new byte[] { 1 }),
                        ByteBuffer.wrap(new byte[] { 2 }),
                        ByteBuffer.wrap(new byte[] { 3 }))));
    }
    @Test
    public void testWrittenBuffersEquals() {
        //XXX Same tests than testEquals with written AggregateChannelBuffers
        ChannelBuffer a, b;
        // Different length.
        a = wrappedBuffer(order, new byte[] { 1  });
        b = wrappedBuffer(wrappedBuffer(order, new byte[] { 1 }, new byte[1]));
        // to enable writeBytes
        b.writerIndex(b.writerIndex()-1);
        b.writeBytes(
                wrappedBuffer(order, new byte[] { 2 }));
        assertFalse(ChannelBuffers.equals(a, b));

        // Same content, same firstIndex, short length.
        a = wrappedBuffer(order, new byte[] { 1, 2, 3 });
        b = wrappedBuffer(wrappedBuffer(order, new byte[] { 1 }, new byte[2]));
        // to enable writeBytes
        b.writerIndex(b.writerIndex()-2);
        b.writeBytes(
                wrappedBuffer(order, new byte[] { 2 }));
        b.writeBytes(wrappedBuffer(order, new byte[] { 3 }));
        assertTrue(ChannelBuffers.equals(a, b));

        // Same content, different firstIndex, short length.
        a = wrappedBuffer(order, new byte[] { 1, 2, 3 });
        b = wrappedBuffer(wrappedBuffer(order, new byte[] { 0, 1, 2, 3, 4 }, 1, 3));
        // to enable writeBytes
        b.writerIndex(b.writerIndex()-1);
        b.writeBytes(
                wrappedBuffer(order, new byte[] { 0, 1, 2, 3, 4 }, 3, 1));
        assertTrue(ChannelBuffers.equals(a, b));

        // Different content, same firstIndex, short length.
        a = wrappedBuffer(order, new byte[] { 1, 2, 3 });
        b = wrappedBuffer(wrappedBuffer(order, new byte[] { 1, 2 }, new byte[1]));
        // to enable writeBytes
        b.writerIndex(b.writerIndex()-1);
        b.writeBytes(
                wrappedBuffer(order, new byte[] { 4 }));
        assertFalse(ChannelBuffers.equals(a, b));

        // Different content, different firstIndex, short length.
        a = wrappedBuffer(order, new byte[] { 1, 2, 3 });
        b = wrappedBuffer(wrappedBuffer(order, new byte[] { 0, 1, 2, 4, 5 }, 1, 3));
        // to enable writeBytes
        b.writerIndex(b.writerIndex()-1);
        b.writeBytes(
                wrappedBuffer(order, new byte[] { 0, 1, 2, 4, 5 }, 3, 1));
        assertFalse(ChannelBuffers.equals(a, b));

        // Same content, same firstIndex, long length.
        a = wrappedBuffer(order, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
        b = wrappedBuffer(wrappedBuffer(order, new byte[] { 1, 2, 3 }, new byte[7]));
        // to enable writeBytes
        b.writerIndex(b.writerIndex()-7);
        b.writeBytes(
                wrappedBuffer(order, new byte[] { 4, 5, 6 }));
        b.writeBytes(
                wrappedBuffer(order, new byte[] { 7, 8, 9, 10 }));
        assertTrue(ChannelBuffers.equals(a, b));

        // Same content, different firstIndex, long length.
        a = wrappedBuffer(order, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
        b = wrappedBuffer(wrappedBuffer(order, new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 1, 10));
        // to enable writeBytes
        b.writerIndex(b.writerIndex()-5);
        b.writeBytes(
                wrappedBuffer(order, new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 6, 5));
        assertTrue(ChannelBuffers.equals(a, b));

        // Different content, same firstIndex, long length.
        a = wrappedBuffer(order, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
        b = wrappedBuffer(wrappedBuffer(order, new byte[] { 1, 2, 3, 4, 6 }, new byte[5]));
        // to enable writeBytes
        b.writerIndex(b.writerIndex()-5);
        b.writeBytes(
                wrappedBuffer(order, new byte[] { 7, 8, 5, 9, 10 }));
        assertFalse(ChannelBuffers.equals(a, b));

        // Different content, different firstIndex, long length.
        a = wrappedBuffer(order, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
        b = wrappedBuffer(wrappedBuffer(order, new byte[] { 0, 1, 2, 3, 4, 6, 7, 8, 5, 9, 10, 11 }, 1, 10));
        // to enable writeBytes
        b.writerIndex(b.writerIndex()-5);
        b.writeBytes(
                wrappedBuffer(order, new byte[] { 0, 1, 2, 3, 4, 6, 7, 8, 5, 9, 10, 11 }, 6, 5));
        assertFalse(ChannelBuffers.equals(a, b));
    }
}
