/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.util.internal;

import static org.easymock.EasyMock.*;
import static org.easymock.classextension.EasyMock.*;
import static org.junit.Assert.*;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.DefaultChannelPipeline;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.junit.Test;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
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
