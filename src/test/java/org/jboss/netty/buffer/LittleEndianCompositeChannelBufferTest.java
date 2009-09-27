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

import static org.jboss.netty.buffer.ChannelBuffers.copiedBuffer;
import static org.jboss.netty.buffer.ChannelBuffers.dynamicBuffer;
import static org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer;
import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
public class LittleEndianCompositeChannelBufferTest extends AbstractChannelBufferTest {

    private List<ChannelBuffer> buffers;
    private ChannelBuffer buffer;

    @Override
    protected ChannelBuffer newBuffer(int length) {
        buffers = new ArrayList<ChannelBuffer>();
        for (int i = 0; i < length; i += 10) {
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[1]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[2]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[3]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[4]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[5]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[6]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[7]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[8]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[9]));
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
    
    private static final int CAPACITY2 = 4096; // Must be even
    
    // Override the default from AbstractChannelBufferTest
       @Test
       public void testDiscardReadBytes() {
           ChannelBuffer a, b;
           a = wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
           b = wrappedBuffer(
                   wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }, 0, 5),
                   wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }, 5, 5));
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

           // From AbstractCB
           buffer.writerIndex(0);
           for (int i = 0; i < buffer.capacity(); i += 4) {
               buffer.writeInt(i);
           }
           ChannelBuffer copy = copiedBuffer(buffer);

           // Make sure there's no effect if called when readerIndex is 0.
           buffer.readerIndex(CAPACITY2 / 4);
           buffer.markReaderIndex();
           buffer.writerIndex(CAPACITY2 / 3);
           buffer.markWriterIndex();
           buffer.readerIndex(0);
           buffer.writerIndex(CAPACITY2 / 2);
           buffer.discardReadBytes();

           assertEquals(0, buffer.readerIndex());
           assertEquals(CAPACITY2 / 2, buffer.writerIndex());
           assertEquals(copy.slice(0, CAPACITY2 / 2), buffer.slice(0, CAPACITY2 / 2));
           buffer.resetReaderIndex();
           assertEquals(CAPACITY2 / 4, buffer.readerIndex());
           buffer.resetWriterIndex();
           assertEquals(CAPACITY2 / 3, buffer.writerIndex());

           // Make sure bytes after writerIndex is not copied.
           buffer.readerIndex(1);
           buffer.writerIndex(CAPACITY2 / 2);
           buffer.discardReadBytes();

           assertEquals(0, buffer.readerIndex());
           assertEquals(CAPACITY2 / 2 - 1, buffer.writerIndex());
           assertEquals(copy.slice(1, CAPACITY2 / 2 - 1), buffer.slice(0, CAPACITY2 / 2 - 1));
           assertEquals(copy.slice(CAPACITY2 / 2, CAPACITY2 / 2), buffer.slice(CAPACITY2 / 2 - 1, CAPACITY2 / 2));

           // Marks also should be relocated.
           buffer.resetReaderIndex();
           assertEquals(CAPACITY2 / 4 - 1, buffer.readerIndex());
           buffer.resetWriterIndex();
           assertEquals(CAPACITY2 / 3 - 1, buffer.writerIndex());

           // Read a lot
           buffer.readerIndex(0);
           int len = CAPACITY2 - 1; // previous valid bytes
           buffer.writerIndex(len);
           int read = CAPACITY2 / 2 - 1; // read half
           buffer.skipBytes(read);
           buffer.discardReadBytes();

           assertEquals(0, buffer.readerIndex());
           assertEquals(len-read, buffer.writerIndex());
           assertEquals(copy.slice(read+1, len-read), buffer.slice(0, len-read));
           // check if slice is not starting at 0 if it is still OK
           assertEquals(copy.slice(read+1+100, len-read-100), buffer.slice(100, len-read-100));
       }
       
       @Test
       public void testCompositeWrappedBuffer() {
           ChannelBuffer header = dynamicBuffer(ChannelBuffers.LITTLE_ENDIAN, 12);
           ChannelBuffer payload = dynamicBuffer(ChannelBuffers.LITTLE_ENDIAN, 512);

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
           a = wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1  });
           b = wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1 }),
                   wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 2 }));
           assertFalse(ChannelBuffers.equals(a, b));

           // Same content, same firstIndex, short length.
           a = wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3 });
           b = wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1 }),
                   wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 2 }),
                   wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 3 }));
           assertTrue(ChannelBuffers.equals(a, b));

           // Same content, different firstIndex, short length.
           a = wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3 });
           b = wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 0, 1, 2, 3, 4 }, 1, 2),
                   wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 0, 1, 2, 3, 4 }, 3, 1));
           assertTrue(ChannelBuffers.equals(a, b));

           // Different content, same firstIndex, short length.
           a = wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3 });
           b = wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2 }),
                   wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 4 }));
           assertFalse(ChannelBuffers.equals(a, b));

           // Different content, different firstIndex, short length.
           a = wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3 });
           b = wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 0, 1, 2, 4, 5 }, 1, 2),
                   wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 0, 1, 2, 4, 5 }, 3, 1));
           assertFalse(ChannelBuffers.equals(a, b));

           // Same content, same firstIndex, long length.
           a = wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
           b = wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3 }),
                   wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 4, 5, 6 }),
                   wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 7, 8, 9, 10 }));
           assertTrue(ChannelBuffers.equals(a, b));

           // Same content, different firstIndex, long length.
           a = wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
           b = wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 1, 5),
                   wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 6, 5));
           assertTrue(ChannelBuffers.equals(a, b));

           // Different content, same firstIndex, long length.
           a = wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
           b = wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3, 4, 6 }),
                   wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 7, 8, 5, 9, 10 }));
           assertFalse(ChannelBuffers.equals(a, b));

           // Different content, different firstIndex, long length.
           a = wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
           b = wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 0, 1, 2, 3, 4, 6, 7, 8, 5, 9, 10, 11 }, 1, 5),
                   wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 0, 1, 2, 3, 4, 6, 7, 8, 5, 9, 10, 11 }, 6, 5));
           assertFalse(ChannelBuffers.equals(a, b));
       }
       @Test
       public void testWrappedBuffer() {

           assertEquals(16, wrappedBuffer(wrappedBuffer(ByteBuffer.allocateDirect(16))).capacity());

           assertEquals(
                   wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3 })),
                   wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[][] { new byte[] { 1, 2, 3 } })));

           assertEquals(
                   wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3 })),
                   wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, 
                           new byte[] { 1 },
                           new byte[] { 2 },
                           new byte[] { 3 })));

           assertEquals(
                   wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3 })),
                   wrappedBuffer(new ChannelBuffer[] {
                           wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3 })
                   }));

           assertEquals(
                   wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3 })),
                   wrappedBuffer(
                           wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1 }),
                           wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 2 }),
                           wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 3 })));

           assertEquals(
                   wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3 })),
                   wrappedBuffer(wrappedBuffer(new ByteBuffer[] {
                           ByteBuffer.wrap(new byte[] { 1, 2, 3 })
                   })));

           assertEquals(
                   wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3 })),
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
           a = wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1  });
           b = wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1 }, new byte[1]));
           // to enable writeBytes
           b.writerIndex(b.writerIndex()-1);
           b.writeBytes(
                   wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 2 }));
           assertFalse(ChannelBuffers.equals(a, b));

           // Same content, same firstIndex, short length.
           a = wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3 });
           b = wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1 }, new byte[2]));
           // to enable writeBytes
           b.writerIndex(b.writerIndex()-2);
           b.writeBytes(
                   wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 2 }));
           b.writeBytes(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 3 }));
           assertTrue(ChannelBuffers.equals(a, b));

           // Same content, different firstIndex, short length.
           a = wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3 });
           b = wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 0, 1, 2, 3, 4 }, 1, 3));
           // to enable writeBytes
           b.writerIndex(b.writerIndex()-1);
           b.writeBytes(
                   wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 0, 1, 2, 3, 4 }, 3, 1));
           assertTrue(ChannelBuffers.equals(a, b));

           // Different content, same firstIndex, short length.
           a = wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3 });
           b = wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2 }, new byte[1]));
           // to enable writeBytes
           b.writerIndex(b.writerIndex()-1);
           b.writeBytes(
                   wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 4 }));
           assertFalse(ChannelBuffers.equals(a, b));

           // Different content, different firstIndex, short length.
           a = wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3 });
           b = wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 0, 1, 2, 4, 5 }, 1, 3));
           // to enable writeBytes
           b.writerIndex(b.writerIndex()-1);
           b.writeBytes(
                   wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 0, 1, 2, 4, 5 }, 3, 1));
           assertFalse(ChannelBuffers.equals(a, b));

           // Same content, same firstIndex, long length.
           a = wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
           b = wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3 }, new byte[7]));
           // to enable writeBytes
           b.writerIndex(b.writerIndex()-7);
           b.writeBytes(
                   wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 4, 5, 6 }));
           b.writeBytes(
                   wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 7, 8, 9, 10 }));
           assertTrue(ChannelBuffers.equals(a, b));

           // Same content, different firstIndex, long length.
           a = wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
           b = wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 1, 10));
           // to enable writeBytes
           b.writerIndex(b.writerIndex()-5);
           b.writeBytes(
                   wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 6, 5));
           assertTrue(ChannelBuffers.equals(a, b));

           // Different content, same firstIndex, long length.
           a = wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
           b = wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3, 4, 6 }, new byte[5]));
           // to enable writeBytes
           b.writerIndex(b.writerIndex()-5);
           b.writeBytes(
                   wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 7, 8, 5, 9, 10 }));
           assertFalse(ChannelBuffers.equals(a, b));

           // Different content, different firstIndex, long length.
           a = wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
           b = wrappedBuffer(wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 0, 1, 2, 3, 4, 6, 7, 8, 5, 9, 10, 11 }, 1, 10));
           // to enable writeBytes
           b.writerIndex(b.writerIndex()-5);
           b.writeBytes(
                   wrappedBuffer(ChannelBuffers.LITTLE_ENDIAN, new byte[] { 0, 1, 2, 3, 4, 6, 7, 8, 5, 9, 10, 11 }, 6, 5));
           assertFalse(ChannelBuffers.equals(a, b));
       }
}
