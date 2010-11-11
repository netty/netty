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
package org.jboss.netty.util.internal;

import static org.easymock.EasyMock.*;
import static org.easymock.classextension.EasyMock.*;
import static org.junit.Assert.*;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.DefaultChannelPipeline;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.util.ThreadRenamingRunnable;
import org.junit.Test;


/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 */
public class StackTraceSimplifierTest {

    @Test
    public void testBasicSimplification() {
        Exception e = new Exception();
        e.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(ChannelBuffer.class.getName(), "a", null, 1),
                new StackTraceElement("com.example.Foo", "b", null, 1),
                new StackTraceElement(SimpleChannelHandler.class.getName(), "c", null, 1),
                new StackTraceElement(ThreadRenamingRunnable.class.getName(), "d", null, 1),
        });

        StackTraceSimplifier.simplify(e);

        StackTraceElement[] simplified = e.getStackTrace();
        assertEquals(2, simplified.length);
        assertEquals(ChannelBuffer.class.getName(), simplified[0].getClassName());
        assertEquals("com.example.Foo", simplified[1].getClassName());
    }

    @Test
    public void testNestedSimplification() {
        Exception e1 = new Exception();
        e1.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(ChannelBuffer.class.getName(), "a", null, 1),
                new StackTraceElement("com.example.Foo", "b", null, 1),
                new StackTraceElement(SimpleChannelHandler.class.getName(), "c", null, 1),
                new StackTraceElement(DefaultChannelPipeline.class.getName(), "d", null, 1),
                new StackTraceElement(ThreadRenamingRunnable.class.getName(), "e", null, 1),
        });

        Exception e2 = new Exception(e1);
        e2.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(Channel.class.getName(), "a", null, 1),
                new StackTraceElement("com.example.Bar", "b", null, 1),
                new StackTraceElement(SimpleChannelHandler.class.getName(), "c", null, 1),
                new StackTraceElement(DefaultChannelPipeline.class.getName(), "d", null, 1),
                new StackTraceElement(ThreadRenamingRunnable.class.getName(), "e", null, 1),
        });

        StackTraceSimplifier.simplify(e2);

        StackTraceElement[] simplified1 = e1.getStackTrace();
        assertEquals(2, simplified1.length);
        assertEquals(ChannelBuffer.class.getName(), simplified1[0].getClassName());
        assertEquals("com.example.Foo", simplified1[1].getClassName());

        StackTraceElement[] simplified2 = e2.getStackTrace();
        assertEquals(2, simplified2.length);
        assertEquals(Channel.class.getName(), simplified2[0].getClassName());
        assertEquals("com.example.Bar", simplified2[1].getClassName());
    }

    @Test
    public void testNettyBugDetection() {
        Exception e = new Exception();
        e.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(DefaultChannelPipeline.class.getName(), "a", null, 1),
                new StackTraceElement(ChannelBuffer.class.getName(), "a", null, 1),
                new StackTraceElement("com.example.Foo", "b", null, 1),
                new StackTraceElement(SimpleChannelHandler.class.getName(), "c", null, 1),
                new StackTraceElement(ThreadRenamingRunnable.class.getName(), "d", null, 1),
        });

        StackTraceSimplifier.simplify(e);

        StackTraceElement[] simplified = e.getStackTrace();
        assertEquals(5, simplified.length);
    }

    @Test
    public void testEmptyStackTrace() {
        Exception e = new Exception();
        e.setStackTrace(new StackTraceElement[0]);

        StackTraceSimplifier.simplify(e);
        assertEquals(0, e.getStackTrace().length);
    }


    @Test
    public void testNullStackTrace() {
        Exception e = createNiceMock(Exception.class);
        expect(e.getStackTrace()).andReturn(null).anyTimes();
        replay(e);

        StackTraceSimplifier.simplify(e);
        assertNull(e.getStackTrace());
        verify(e);
    }
}
