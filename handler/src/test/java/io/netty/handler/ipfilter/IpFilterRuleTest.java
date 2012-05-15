/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ipfilter;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import junit.framework.TestCase;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.UpstreamMessageEvent;
import org.junit.Test;

public class IpFilterRuleTest extends TestCase
{
    public static boolean accept(IpFilterRuleHandler h, InetSocketAddress addr) throws Exception
    {
        return h.accept(new ChannelHandlerContext()
        {

            @Override
            public boolean canHandleDownstream()
            {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public boolean canHandleUpstream()
            {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public Object getAttachment()
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public Channel getChannel()
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public ChannelHandler getHandler()
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public String getName()
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public ChannelPipeline getPipeline()
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public void sendDownstream(ChannelEvent e)
            {
                // TODO Auto-generated method stub
                
            }

            @Override
            public void sendUpstream(ChannelEvent e)
            {
                // TODO Auto-generated method stub
                
            }

            @Override
            public void setAttachment(Object attachment)
            {
                // TODO Auto-generated method stub
                
            }
            
        }, 
        new UpstreamMessageEvent(new Channel()
        {

            @Override
            public ChannelFuture bind(SocketAddress localAddress)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public ChannelFuture close()
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public ChannelFuture connect(SocketAddress remoteAddress)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public ChannelFuture disconnect()
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public ChannelFuture getCloseFuture()
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public ChannelConfig getConfig()
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public ChannelFactory getFactory()
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public Integer getId()
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public int getInterestOps()
            {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public SocketAddress getLocalAddress()
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public Channel getParent()
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public ChannelPipeline getPipeline()
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public SocketAddress getRemoteAddress()
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public boolean isBound()
            {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public boolean isConnected()
            {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public boolean isOpen()
            {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public boolean isReadable()
            {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public boolean isWritable()
            {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public ChannelFuture setInterestOps(int interestOps)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public ChannelFuture setReadable(boolean readable)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public ChannelFuture unbind()
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public ChannelFuture write(Object message)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public ChannelFuture write(Object message, SocketAddress remoteAddress)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public int compareTo(Channel o)
            {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public Object getAttachment() {
                return null;
            }

            @Override
            public void setAttachment(Object attachment) {
                
            }
            
        }, h, addr), 
        addr);
    }
    
    @Test
    public void testIpFilterRule() throws Exception
    {
        IpFilterRuleHandler h = new IpFilterRuleHandler();
        h.addAll(new IpFilterRuleList("+n:localhost, -n:*"));
        InetSocketAddress addr = new InetSocketAddress(InetAddress.getLocalHost(), 8080);
        assertTrue(accept(h, addr));
        addr = new InetSocketAddress(InetAddress.getByName("127.0.0.2"), 8080);
        assertFalse(accept(h, addr));
        addr = new InetSocketAddress(InetAddress.getByName(InetAddress.getLocalHost().getHostName()), 8080);
        assertTrue(accept(h, addr));
        
        h.clear();
        h.addAll(new IpFilterRuleList("+n:*"+InetAddress.getLocalHost().getHostName().substring(1)+", -n:*"));
        addr = new InetSocketAddress(InetAddress.getLocalHost(), 8080);
        assertTrue(accept(h, addr));
        addr = new InetSocketAddress(InetAddress.getByName("127.0.0.2"), 8080);
        assertFalse(accept(h, addr));
        addr = new InetSocketAddress(InetAddress.getByName(InetAddress.getLocalHost().getHostName()), 8080);
        assertTrue(accept(h, addr));

        h.clear();
        h.addAll(new IpFilterRuleList("+c:"+InetAddress.getLocalHost().getHostAddress()+"/32, -n:*"));
        addr = new InetSocketAddress(InetAddress.getLocalHost(), 8080);
        assertTrue(accept(h, addr));
        addr = new InetSocketAddress(InetAddress.getByName("127.0.0.2"), 8080);
        assertFalse(accept(h, addr));
        addr = new InetSocketAddress(InetAddress.getByName(InetAddress.getLocalHost().getHostName()), 8080);
        assertTrue(accept(h, addr));

        h.clear();
        h.addAll(new IpFilterRuleList(""));
        addr = new InetSocketAddress(InetAddress.getLocalHost(), 8080);
        assertTrue(accept(h, addr));
        addr = new InetSocketAddress(InetAddress.getByName("127.0.0.2"), 8080);
        assertTrue(accept(h, addr));
        addr = new InetSocketAddress(InetAddress.getByName(InetAddress.getLocalHost().getHostName()), 8080);
        assertTrue(accept(h, addr));

        h.clear();
        addr = new InetSocketAddress(InetAddress.getLocalHost(), 8080);
        assertTrue(accept(h, addr));
        addr = new InetSocketAddress(InetAddress.getByName("127.0.0.2"), 8080);
        assertTrue(accept(h, addr));
        addr = new InetSocketAddress(InetAddress.getByName(InetAddress.getLocalHost().getHostName()), 8080);
        assertTrue(accept(h, addr));

    }

}
