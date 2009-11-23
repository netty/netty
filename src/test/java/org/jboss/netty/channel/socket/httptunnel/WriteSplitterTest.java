package org.jboss.netty.channel.socket.httptunnel;


import static org.junit.Assert.*;

import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.junit.Before;
import org.junit.Test;

public class WriteSplitterTest {

    private static final int SPLIT_THRESHOLD = 1024;
    
    private WriteSplitter splitter;

    @Before
    public void setUp() throws Exception {
        splitter = new WriteSplitter(SPLIT_THRESHOLD);
    }
    
    @Test
    public void testSplit_bufferUnderThreshold() {
        ChannelBuffer buffer = createBufferWithContents(800);
        List<ChannelBuffer> fragments = splitter.split(buffer);
        assertNotNull(fragments);
        assertEquals(1, fragments.size());
    }
    
    @Test
    public void testSplit_bufferMatchesThreshold() {
        ChannelBuffer buffer = createBufferWithContents(SPLIT_THRESHOLD);
        List<ChannelBuffer> fragments = splitter.split(buffer);
        assertNotNull(fragments);
        assertEquals(1, fragments.size());
    }
    
    @Test
    public void testSplit_bufferOverThreshold() {
        ChannelBuffer buffer = createBufferWithContents((int)(SPLIT_THRESHOLD * 1.5));
        List<ChannelBuffer> fragments = splitter.split(buffer);
        assertNotNull(fragments);
        assertEquals(2, fragments.size());
        
        ChannelBuffer fragment1 = fragments.get(0);
        checkMatches(buffer, fragment1);
        ChannelBuffer fragment2 = fragments.get(1);
        checkMatches(buffer, fragment2);
    }
    
    @Test
    public void testSplit_largeNumberOfFragments() {
        ChannelBuffer buffer = createBufferWithContents(SPLIT_THRESHOLD * 250);
        List<ChannelBuffer> fragments = splitter.split(buffer);
        assertNotNull(fragments);
        assertEquals(250, fragments.size());
        
        for(ChannelBuffer fragment : fragments) {
            checkMatches(buffer, fragment);
        }
    }

    private void checkMatches(ChannelBuffer mainBuffer, ChannelBuffer fragment) {
        assertTrue(mainBuffer.readableBytes() >= fragment.readableBytes());
        while(fragment.readable()) {
            assertEquals(mainBuffer.readByte(), fragment.readByte());
        }
    }

    private ChannelBuffer createBufferWithContents(int size) {
        byte[] contents = new byte[size];
        for(int i=0; i < contents.length; i++) {
            contents[i] = (byte)(i % 10);
        }
        
        return ChannelBuffers.copiedBuffer(contents);
    }

}
