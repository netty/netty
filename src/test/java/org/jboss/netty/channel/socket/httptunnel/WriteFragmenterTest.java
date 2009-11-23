package org.jboss.netty.channel.socket.httptunnel;


import static org.junit.Assert.*;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.junit.Before;
import org.junit.Test;

/**
 * @author iain.mcginniss@onedrum.com
 */
public class WriteFragmenterTest {

    private FakeSocketChannel channel;
    private WriteFragmenter fragmenter;
    private FakeChannelSink downstreamCatcher;
    
    @Before
    public void setUp() throws Exception {
        fragmenter = new WriteFragmenter(100);
        
        ChannelPipeline pipeline = Channels.pipeline();
        pipeline.addLast(WriteFragmenter.NAME, fragmenter);
        downstreamCatcher = new FakeChannelSink();
        channel = new FakeSocketChannel(null, null, pipeline, downstreamCatcher);   
    }
    
    @Test
    public void testLeavesWritesBeneathThresholdUntouched() {
        ChannelBuffer data = ChannelBuffers.wrappedBuffer(new byte[99]);
        Channels.write(channel, data);
        
        assertEquals(1, downstreamCatcher.events.size());
        ChannelBuffer sentData = NettyTestUtils.checkIsDownstreamMessageEvent(downstreamCatcher.events.poll(), ChannelBuffer.class);
        assertSame(data, sentData);
    }
    
    @Test
    public void testLeavesMessagesOnThresholdUntouched() {
        ChannelBuffer data = ChannelBuffers.wrappedBuffer(new byte[100]);
        Channels.write(channel, data);
        
        assertEquals(1, downstreamCatcher.events.size());
        ChannelBuffer sentData = NettyTestUtils.checkIsDownstreamMessageEvent(downstreamCatcher.events.poll(), ChannelBuffer.class);
        assertSame(data, sentData);
    }
    
    @Test
    public void testSplitsMessagesAboveThreshold_twoChunks() {
        ChannelBuffer data = ChannelBuffers.wrappedBuffer(new byte[101]);
        Channels.write(channel, data);
        
        assertEquals(2, downstreamCatcher.events.size());
        ChannelBuffer chunk1 = NettyTestUtils.checkIsDownstreamMessageEvent(downstreamCatcher.events.poll(), ChannelBuffer.class);
        ChannelBuffer chunk2 = NettyTestUtils.checkIsDownstreamMessageEvent(downstreamCatcher.events.poll(), ChannelBuffer.class);
        assertEquals(100, chunk1.readableBytes());
        assertEquals(1, chunk2.readableBytes());
    }
    
    @Test
    public void testSplitsMessagesAboveThreshold_multipleChunks() {
        ChannelBuffer data = ChannelBuffers.wrappedBuffer(new byte[2540]);
        Channels.write(channel, data);
        
        assertEquals(26, downstreamCatcher.events.size());
        for(int i=0; i < 25; i++) {
            ChannelBuffer chunk = NettyTestUtils.checkIsDownstreamMessageEvent(downstreamCatcher.events.poll(), ChannelBuffer.class);
            assertEquals(100, chunk.readableBytes());
        }
        
        ChannelBuffer endChunk = NettyTestUtils.checkIsDownstreamMessageEvent(downstreamCatcher.events.poll(), ChannelBuffer.class);
        assertEquals(40, endChunk.readableBytes());
    }
    
    @Test
    public void testChannelFutureTriggeredOnlyWhenAllChunksWritten() {
        ChannelBuffer data = ChannelBuffers.wrappedBuffer(new byte[2540]);
        ChannelFuture mainWriteFuture = Channels.write(channel, data);
        
        assertEquals(26, downstreamCatcher.events.size());
        for(int i=0; i < 25; i++) {
            ((MessageEvent) downstreamCatcher.events.poll()).getFuture().setSuccess();
            assertFalse(mainWriteFuture.isDone());
        }
        
        ((MessageEvent) downstreamCatcher.events.poll()).getFuture().setSuccess();
        assertTrue(mainWriteFuture.isDone());
        assertTrue(mainWriteFuture.isSuccess());
    }
    
    @Test
    public void testChannelFutureFailsOnFirstWriteFailure() {
        ChannelBuffer data = ChannelBuffers.wrappedBuffer(new byte[2540]);
        ChannelFuture mainWriteFuture = Channels.write(channel, data);
        
        assertEquals(26, downstreamCatcher.events.size());
        for(int i=0; i < 10; i++) {
            ((MessageEvent) downstreamCatcher.events.poll()).getFuture().setSuccess();
            assertFalse(mainWriteFuture.isDone());
        }
        
        ((MessageEvent) downstreamCatcher.events.poll()).getFuture().setFailure(new Exception("Something bad happened"));
        assertTrue(mainWriteFuture.isDone());
        assertFalse(mainWriteFuture.isSuccess());
        
        // check all the subsequent writes got cancelled
        for(int i=0; i < 15; i++) {
            assertTrue(((MessageEvent) downstreamCatcher.events.poll()).getFuture().isCancelled());
        }
    }
}
