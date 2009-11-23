package org.jboss.netty.channel.socket.httptunnel;

import static org.junit.Assert.*;

import org.jboss.netty.channel.socket.SocketChannelConfig;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class HttpTunnelClientChannelConfigTest {

	JUnit4Mockery mockContext = new JUnit4Mockery();
	
	SocketChannelConfig sendChannelConfig;
	SocketChannelConfig pollChannelConfig;
	
	HttpTunnelClientChannelConfig config;
	
	@Before
	public void setUp() {
		sendChannelConfig = mockContext.mock(SocketChannelConfig.class, "sendChannelConfig");
		pollChannelConfig = mockContext.mock(SocketChannelConfig.class, "pollChannelConfig");
		
		config = new HttpTunnelClientChannelConfig(sendChannelConfig, pollChannelConfig);
	}
	
	@Test
	public void testGetReceiveBufferSize() {
		mockContext.checking(new Expectations() {{
			one(pollChannelConfig).getReceiveBufferSize(); will(returnValue(100));
		}});
		
		assertEquals(100, config.getReceiveBufferSize());
	}
	
	@Test
	public void testGetSendBufferSize() {
		mockContext.checking(new Expectations() {{
			one(pollChannelConfig).getSendBufferSize(); will(returnValue(100));
		}});
		
		assertEquals(100, config.getSendBufferSize());
	}
	
	@Test
	public void testGetSoLinger() {
		mockContext.checking(new Expectations() {{
			one(pollChannelConfig).getSoLinger(); will(returnValue(100));
		}}); 
		
		assertEquals(100, config.getSoLinger());
	}
	
	@Test
	public void testTrafficClass() {
		mockContext.checking(new Expectations() {{
			one(pollChannelConfig).getTrafficClass(); will(returnValue(1));
		}}); 
		
		assertEquals(1, config.getTrafficClass());
	}
	
	@Test
	public void testIsKeepAlive() {
		mockContext.checking(new Expectations() {{
			one(pollChannelConfig).isKeepAlive(); will(returnValue(true));
		}}); 
		
		assertTrue(config.isKeepAlive());
	}
	
	@Test
	public void testIsReuseAddress() {
		mockContext.checking(new Expectations() {{
			one(pollChannelConfig).isReuseAddress(); will(returnValue(true));
		}}); 
		
		assertTrue(config.isReuseAddress());
	}
	
	@Test
	public void testIsTcpNoDelay() {
		mockContext.checking(new Expectations() {{
			one(pollChannelConfig).isTcpNoDelay(); will(returnValue(true));
		}}); 
		
		assertTrue(config.isTcpNoDelay());
	}
	
	@Test
	public void testSetKeepAlive() {
		mockContext.checking(new Expectations() {{
			one(pollChannelConfig).setKeepAlive(true);
			one(sendChannelConfig).setKeepAlive(true);
		}}); 
		
		config.setKeepAlive(true);
	}
	
	@Test
	public void testSetPerformancePreferences() {
		mockContext.checking(new Expectations() {{
			one(pollChannelConfig).setPerformancePreferences(100, 200, 300);
			one(sendChannelConfig).setPerformancePreferences(100, 200, 300);
		}}); 
		
		config.setPerformancePreferences(100, 200, 300);
	}
	
	@Test
	public void testSetReceiveBufferSize() {
		mockContext.checking(new Expectations() {{
			one(pollChannelConfig).setReceiveBufferSize(100);
			one(sendChannelConfig).setReceiveBufferSize(100);
		}}); 
		
		config.setReceiveBufferSize(100);
	}
	
	@Test
	public void testSetReuseAddress() {
		mockContext.checking(new Expectations() {{
			one(pollChannelConfig).setReuseAddress(true);
			one(sendChannelConfig).setReuseAddress(true);
		}}); 
		
		config.setReuseAddress(true);
	}
	
	@Test
	public void testSetSendBufferSize() {
		mockContext.checking(new Expectations() {{
			one(pollChannelConfig).setSendBufferSize(100);
			one(sendChannelConfig).setSendBufferSize(100);
		}}); 
		
		config.setSendBufferSize(100);
	}
	
	@Test
	public void testSetSoLinger() {
		mockContext.checking(new Expectations() {{
			one(pollChannelConfig).setSoLinger(100);
			one(sendChannelConfig).setSoLinger(100);
		}}); 
		
		config.setSoLinger(100);
	}
	
	@Test
	public void testTcpNoDelay() {
		mockContext.checking(new Expectations() {{
			one(pollChannelConfig).setTcpNoDelay(true);
			one(sendChannelConfig).setTcpNoDelay(true);
		}}); 
		
		config.setTcpNoDelay(true);
	}
	
	@Test
	public void testSetTrafficClass() {
		mockContext.checking(new Expectations() {{
			one(pollChannelConfig).setTrafficClass(1);
			one(sendChannelConfig).setTrafficClass(1);
		}}); 
		
		config.setTrafficClass(1);
	}
	
}
