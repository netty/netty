/*
 * JBoss, Home of Professional Open Source Copyright 2009, Red Hat Middleware
 * LLC, and individual contributors by the @authors tag. See the copyright.txt
 * in the distribution for a full listing of individual contributors.
 * 
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.jboss.netty.channel.socket.nio;

import static org.junit.Assert.*;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.junit.Test;

/**
 * Unit test for {@link NioDatagramChannel}
 * 
 * @author <a href="mailto:dbevenius@jboss.com">Daniel Bevenius</a>
 */
public class NioDatagramChannelTest
{
    private static final int PORT = 9999;
    private static final String HOST = "localhost";
    
    @Test
    public void sendAndRecivePayload() throws Throwable
    {
        final String expectedPayload = "some payload";
        final ServerBootstrap sb = new ServerBootstrap(new NioDatagramChannelFactory(Executors.newCachedThreadPool()));
        final Channel sc = sb.bind(new InetSocketAddress(HOST, PORT));
        
        // Must add the handlers after binding...this is shorly a bug on my part but I'm not
        // sure why yet:( /Daniel
        final TestHandler handler = new TestHandler();
        sc.getPipeline().addFirst("handler", handler);

        InetSocketAddress socketAddress = (InetSocketAddress) sc.getLocalAddress();
        assertEquals(PORT, socketAddress.getPort());

        DatagramSocket clientSocket = new DatagramSocket();
        byte[] payload = expectedPayload.getBytes();
        
        DatagramPacket dp = new DatagramPacket(payload, payload.length, socketAddress.getAddress(), PORT);
        
        clientSocket.send(dp);
        
        // clear the payload just to be sure.
        dp.setData(new byte[payload.length]);
        assertFalse(expectedPayload.equals(new String(dp.getData())));
        
        clientSocket.receive(dp);
        byte[] actualPayload = dp.getData();
        assertEquals(expectedPayload, new String(actualPayload));
        
        sc.close().awaitUninterruptibly();
    }
    
    @ChannelPipelineCoverage("one")
    private class TestHandler extends SimpleChannelHandler
    {
        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception
        {
            final ChannelBuffer cb = (ChannelBuffer) e.getMessage();
            final byte[] actual = new byte[cb.readableBytes()];
            cb.getBytes(0, actual);
            ctx.sendDownstream(e);
        }
    }
}
