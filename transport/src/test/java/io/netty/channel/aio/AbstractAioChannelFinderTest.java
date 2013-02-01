package io.netty.channel.aio;

import static org.junit.Assert.*;
import static org.easymock.EasyMock.*;

import org.junit.Test;

/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
public abstract class AbstractAioChannelFinderTest {

    protected abstract AioChannelFinder create();

    @Test
    public void testNull() throws Exception {
        AioChannelFinder finder = create();
        AbstractAioChannel channel = finder.findChannel(new Runnable() {
            
            @Override
            public void run() {
                // Noop
            }
        });
        assertNull(channel);
    }

    @Test
    public void testRunnableWrappsAbstractAioChannel() throws Exception {
        final Object mockChannel = createMock("mockChannel", AbstractAioChannel.class);
        replay(mockChannel);
        
        Runnable r = new Runnable() {
            
            @SuppressWarnings("unused")
            @Override
            public void run() {
                Object channel = mockChannel;
                // Noop
            }
        };
        AioChannelFinder finder = create();
        AbstractAioChannel channel = finder.findChannel(r);
        assertNotNull(channel);
        
        AbstractAioChannel channel2 = finder.findChannel(r);
        assertNotNull(channel2);
        assertSame(channel2, channel);
        verify(mockChannel);
        reset(mockChannel);
    }
    
    @Test
    public void testRunnableWrappsRunnable() throws Exception {
        final Object mockChannel = createMock("mockChannel", AbstractAioChannel.class);
        replay(mockChannel);
        
        final Runnable r = new Runnable() {
            
            @SuppressWarnings("unused")
            @Override
            public void run() {
                Object channel = mockChannel;
                // Noop
            }
        };
        Runnable r2 = new Runnable() {
            
            @SuppressWarnings("unused")
            @Override
            public void run() {
                Runnable runnable = r;
                // Noop
            }
        };
        AioChannelFinder finder = create();
        AbstractAioChannel channel = finder.findChannel(r2);
        assertNotNull(channel);
        
        AbstractAioChannel channel2 = finder.findChannel(r2);
        assertNotNull(channel2);
        assertSame(channel2, channel);
        verify(mockChannel);
        reset(mockChannel);
    }
}
