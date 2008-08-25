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
package org.jboss.netty.bootstrap;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.junit.Test;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class BootstrapTest {
    @Test(expected = IllegalStateException.class)
    public void shouldNotReturnNullFactory() {
        new Bootstrap().getFactory();
    }

    @Test(expected = IllegalStateException.class)
    public void shouldNotAllowInitialFactoryToChange() {
        new Bootstrap(createMock(ChannelFactory.class)).setFactory(null);
    }

    @Test
    public void shouldNotAllowFactoryToChangeMoreThanOnce() {
        Bootstrap b = new Bootstrap();
        ChannelFactory f = createMock(ChannelFactory.class);
        b.setFactory(f);
        assertSame(f, b.getFactory());

        try {
            b.setFactory(createMock(ChannelFactory.class));
            fail();
        } catch (IllegalStateException e) {
            // Success.
        }
    }

    @Test(expected = NullPointerException.class)
    public void shouldNotAllowNullFactory() {
        new Bootstrap().setFactory(null);
    }

    @Test
    public void shouldHaveNonNullInitialPipeline() {
        assertNotNull(new Bootstrap().getPipeline());
    }

    @Test(expected = NullPointerException.class)
    public void shouldNotAllowNullPipeline() {
        new Bootstrap().setPipeline(null);
    }

    @Test(expected = NullPointerException.class)
    public void shouldNotAllowNullPipelineMap() {
        new Bootstrap().setPipelineAsMap(null);
    }

    @Test
    public void shouldHaveNonNullInitialPipelineFactory() {
        assertNotNull(new Bootstrap().getPipelineFactory());
    }

    @Test
    public void shouldUpdatePipelineFactoryIfPipelineIsSet() {
        Bootstrap b = new Bootstrap();
        ChannelPipelineFactory oldPipelineFactory = b.getPipelineFactory();
        b.setPipeline(createMock(ChannelPipeline.class));
        assertNotSame(oldPipelineFactory, b.getPipelineFactory());
    }

    @Test(expected = IllegalStateException.class)
    public void shouldNotReturnPipelineWhenPipelineFactoryIsSetByUser() {
        Bootstrap b = new Bootstrap();
        b.setPipelineFactory(createMock(ChannelPipelineFactory.class));
        b.getPipeline();
    }

    @Test(expected = IllegalStateException.class)
    public void shouldNotReturnPipelineMapWhenPipelineFactoryIsSetByUser() {
        Bootstrap b = new Bootstrap();
        b.setPipelineFactory(createMock(ChannelPipelineFactory.class));
        b.getPipelineAsMap();
    }

    @Test(expected = NullPointerException.class)
    public void shouldNotAllowNullPipelineFactory() {
        new Bootstrap().setPipelineFactory(null);
    }

    @Test
    public void shouldHaveInitialEmptyPipelineMap() {
        assertTrue(new Bootstrap().getPipelineAsMap().isEmpty());
    }

    @Test
    public void shouldReturnOrderedPipelineMap() {
        Bootstrap b = new Bootstrap();
        ChannelPipeline p = b.getPipeline();
        p.addLast("a", createMock(ChannelDownstreamHandler.class));
        p.addLast("b", createMock(ChannelDownstreamHandler.class));
        p.addLast("c", createMock(ChannelDownstreamHandler.class));
        p.addLast("d", createMock(ChannelDownstreamHandler.class));

        Iterator<Entry<String, ChannelHandler>> m =
            b.getPipelineAsMap().entrySet().iterator();
        Entry<String, ChannelHandler> e;
        e = m.next();
        assertEquals("a", e.getKey());
        assertSame(p.get("a"), e.getValue());
        e = m.next();
        assertEquals("b", e.getKey());
        assertSame(p.get("b"), e.getValue());
        e = m.next();
        assertEquals("c", e.getKey());
        assertSame(p.get("c"), e.getValue());
        e = m.next();
        assertEquals("d", e.getKey());
        assertSame(p.get("d"), e.getValue());

        assertFalse(m.hasNext());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotAllowUnorderedPipelineMap() {
        Map<String, ChannelHandler> m = new HashMap<String, ChannelHandler>();
        m.put("a", createMock(ChannelDownstreamHandler.class));
        m.put("b", createMock(ChannelDownstreamHandler.class));
        m.put("c", createMock(ChannelDownstreamHandler.class));
        m.put("d", createMock(ChannelDownstreamHandler.class));

        Bootstrap b = new Bootstrap();
        b.setPipelineAsMap(m);
    }

    @Test
    public void shouldHaveOrderedPipelineWhenSetFromMap() {
        Logger.getGlobal().setLevel(Level.SEVERE);
        Map<String, ChannelHandler> m = new LinkedHashMap<String, ChannelHandler>();
        m.put("a", createMock(ChannelDownstreamHandler.class));
        m.put("b", createMock(ChannelDownstreamHandler.class));
        m.put("c", createMock(ChannelDownstreamHandler.class));
        m.put("d", createMock(ChannelDownstreamHandler.class));

        Bootstrap b = new Bootstrap();
        b.setPipelineAsMap(m);

        ChannelPipeline p = b.getPipeline();

        assertSame(p.getFirst(), m.get("a"));
        assertEquals("a", p.getContext(p.getFirst()).getName());
        p.removeFirst();
        assertSame(p.getFirst(), m.get("b"));
        assertEquals("b", p.getContext(p.getFirst()).getName());
        p.removeFirst();
        assertSame(p.getFirst(), m.get("c"));
        assertEquals("c", p.getContext(p.getFirst()).getName());
        p.removeFirst();
        assertSame(p.getFirst(), m.get("d"));
        assertEquals("d", p.getContext(p.getFirst()).getName());
        p.removeFirst();

        try {
            p.removeFirst();
            fail();
        } catch (NoSuchElementException e) {
            // Success.
        }
    }

    @Test
    public void shouldHaveInitialEmptyOptionMap() {
        assertTrue(new Bootstrap().getOptions().isEmpty());
    }

    @Test
    public void shouldUpdateOptionMapAsRequested1() {
        Bootstrap b = new Bootstrap();
        b.setOption("s", "x");
        b.setOption("b", true);
        b.setOption("i", 42);

        Map<String, Object> o = b.getOptions();
        assertEquals(3, o.size());
        assertEquals("x", o.get("s"));
        assertEquals(true, o.get("b"));
        assertEquals(42, o.get("i"));
    }

    @Test
    public void shouldUpdateOptionMapAsRequested2() {
        Bootstrap b = new Bootstrap();
        Map<String, Object> o1 = new HashMap<String, Object>();
        o1.put("s", "x");
        o1.put("b", true);
        o1.put("i", 42);
        b.setOptions(o1);

        Map<String, Object> o2 = b.getOptions();
        assertEquals(3, o2.size());
        assertEquals("x", o2.get("s"));
        assertEquals(true, o2.get("b"));
        assertEquals(42, o2.get("i"));

        assertNotSame(o1, o2);
        assertEquals(o1, o2);
    }

    @Test
    public void shouldRemoveOptionIfValueIsNull() {
        Bootstrap b = new Bootstrap();

        b.setOption("s", "x");
        assertEquals("x", b.getOption("s"));

        b.setOption("s", null);
        assertNull(b.getOption("s"));
        assertTrue(b.getOptions().isEmpty());
    }

    @Test(expected = NullPointerException.class)
    public void shouldNotAllowNullOptionKeyOnGet() {
        new Bootstrap().getOption(null);
    }

    @Test(expected = NullPointerException.class)
    public void shouldNotAllowNullOptionKeyOnSet() {
        new Bootstrap().setOption(null, "x");
    }

    @Test(expected = NullPointerException.class)
    public void shouldNotAllowNullOptionMap() {
        new Bootstrap().setOptions(null);
    }
}
