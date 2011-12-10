package org.jboss.netty.channel;

import static org.junit.Assert.*;

import org.junit.Test;

public class TestDefaultChannelPipeline {
	@Test
	public void testReplaceChannelHandler() {
		DefaultChannelPipeline pipeline = new DefaultChannelPipeline();
		
		SimpleChannelHandler handler1 = new SimpleChannelHandler();
		pipeline.addLast("handler1", handler1);
		pipeline.addLast("handler2", handler1);
		pipeline.addLast("handler3", handler1);
		assertTrue(pipeline.get("handler1") == handler1);
		assertTrue(pipeline.get("handler2") == handler1);
		assertTrue(pipeline.get("handler3") == handler1);
		
		SimpleChannelHandler newHandler1 = new SimpleChannelHandler();
		pipeline.replace("handler1", "handler1", newHandler1);
		assertTrue(pipeline.get("handler1") == newHandler1);
		
		SimpleChannelHandler newHandler3 = new SimpleChannelHandler();
		pipeline.replace("handler3", "handler3", newHandler3);
		assertTrue(pipeline.get("handler3") == newHandler3);
		
		SimpleChannelHandler newHandler2 = new SimpleChannelHandler();
		pipeline.replace("handler2", "handler2", newHandler2);
		assertTrue(pipeline.get("handler2") == newHandler2);
	}
}
