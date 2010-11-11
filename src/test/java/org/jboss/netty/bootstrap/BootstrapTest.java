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
package org.jboss.netty.bootstrap;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Map.Entry;

import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.util.DummyHandler;
import org.junit.Test;


/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 */
public class BootstrapTest {
    @Test(expected = IllegalStateException.class)
    public void shouldNotReturnNullFactory() {
        new Bootstrap().getFactory();
    }

    @Test(expected = IllegalStateException.class)
    public void shouldNotAllowInitialFactoryToChange() {
        new Bootstrap(createMock(ChannelFactory.class)).setFactory(createMock(ChannelFactory.class));
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
        p.addLast("a", new DummyHandler());
        p.addLast("b", new DummyHandler());
        p.addLast("c", new DummyHandler());
        p.addLast("d", new DummyHandler());

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
        m.put("a", new DummyHandler());
        m.put("b", new DummyHandler());
        m.put("c", new DummyHandler());
        m.put("d", new DummyHandler());

        Bootstrap b = new Bootstrap();
        b.setPipelineAsMap(m);
    }

    @Test
    public void shouldHaveOrderedPipelineWhenSetFromMap() {
        Map<String, ChannelHandler> m = new LinkedHashMap<String, ChannelHandler>();
        m.put("a", new DummyHandler());
        m.put("b", new DummyHandler());
        m.put("c", new DummyHandler());
        m.put("d", new DummyHandler());

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
