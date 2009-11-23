package org.jboss.netty.channel.socket.httptunnel;

import static org.junit.Assert.*;

import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.junit.Test;

/**
 * @author iain.mcginniss@onedrum.com
 */
public class NettyTestUtilsTest {

    @Test
    public void testSplitIntoChunks() {
        ChannelBuffer a = createFullBuffer(20, (byte)0);
        ChannelBuffer b = createFullBuffer(20, (byte)1);
        ChannelBuffer c = createFullBuffer(20, (byte)2);
        
        List<ChannelBuffer> chunks = NettyTestUtils.splitIntoChunks(10, a, b, c);
        assertEquals(6, chunks.size());
        for(ChannelBuffer chunk : chunks) {
            assertEquals(10, chunk.readableBytes());
        }
        
        // reader index should not be modified by splitIntoChunks()
        assertEquals(0, a.readerIndex());
        assertEquals(0, b.readerIndex());
        assertEquals(0, c.readerIndex());
    }
    
    @Test
    public void testSplitIntoChunks_chunksCrossBoundaries() {
        ChannelBuffer a = createFullBuffer(5, (byte)0);
        ChannelBuffer b = createFullBuffer(5, (byte)1);
        ChannelBuffer c = createFullBuffer(5, (byte)2);
        
        List<ChannelBuffer> chunks = NettyTestUtils.splitIntoChunks(4, a, b, c);
        assertEquals(4, chunks.size());
        checkBufferContains(chunks.get(0), new byte[] { 0, 0, 0, 0 });
        checkBufferContains(chunks.get(1), new byte[] { 0, 1, 1, 1 });
        checkBufferContains(chunks.get(2), new byte[] { 1, 1, 2, 2 });
        checkBufferContains(chunks.get(3), new byte[] { 2, 2, 2 });
    }
    
    @Test
    public void testSplitIntoChunks_smallestChunksPossible() {
        ChannelBuffer a = createFullBuffer(5, (byte)0);
        ChannelBuffer b = createFullBuffer(5, (byte)1);
        ChannelBuffer c = createFullBuffer(5, (byte)2);
        
        List<ChannelBuffer> chunks = NettyTestUtils.splitIntoChunks(1, a, b, c);
        assertEquals(15, chunks.size());
        checkBufferContains(chunks.get(0), new byte[] { 0 });
        checkBufferContains(chunks.get(5), new byte[] { 1 });
        checkBufferContains(chunks.get(10), new byte[] { 2 });
    }
    
    @Test
    public void testSplitIntoChunks_sourceBuffersArePartiallyRead() {
        ChannelBuffer a = createFullBuffer(5, (byte)0);
        a.readerIndex(1);
        ChannelBuffer b = createFullBuffer(5, (byte)1);
        b.readerIndex(2);
        ChannelBuffer c = createFullBuffer(5, (byte)2);
        
        // will be ignored, as fully read
        ChannelBuffer d = createFullBuffer(5, (byte)3);
        d.readerIndex(5);
        ChannelBuffer e = createFullBuffer(5, (byte)4);
        e.readerIndex(4);
        
        List<ChannelBuffer> chunks = NettyTestUtils.splitIntoChunks(3, a, b, c, d, e);
        checkBufferContains(chunks.get(0), new byte[] { 0, 0, 0 });
        checkBufferContains(chunks.get(1), new byte[] { 0, 1, 1 });
        checkBufferContains(chunks.get(2), new byte[] { 1, 2, 2 });
        checkBufferContains(chunks.get(3), new byte[] { 2, 2, 2 });
        checkBufferContains(chunks.get(4), new byte[] { 4 });
    }

    private void checkBufferContains(ChannelBuffer channelBuffer, byte[] bs) {
        if(channelBuffer.readableBytes() != bs.length) {
            fail("buffer does not have enough bytes");
        }
        
        for(int i=0; i < bs.length; i++) {
            assertEquals("byte at position " + i + " does not match", bs[i], channelBuffer.getByte(i));
        }
    }

    private ChannelBuffer createFullBuffer(int size, byte value) {
        ChannelBuffer buffer = ChannelBuffers.buffer(size);
        byte[] contents = new byte[size];
        for(int i=0; i < contents.length; i++) {
            contents[i] = value;
        }
        buffer.writeBytes(contents);
        return buffer;
    }
    
}
