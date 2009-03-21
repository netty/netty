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
package org.jboss.netty.util;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.junit.Test;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class MapBackedSetTest {

    @Test
    @SuppressWarnings("unchecked")
    public void testSize() {
        Map map = createStrictMock(Map.class);
        expect(map.size()).andReturn(0);
        replay(map);

        assertEquals(0, new MapBackedSet(map).size());
        verify(map);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testContains() {
        Map map = createStrictMock(Map.class);
        expect(map.containsKey("key")).andReturn(true);
        replay(map);

        assertTrue(new MapBackedSet(map).contains("key"));
        verify(map);
    }


    @Test
    @SuppressWarnings("unchecked")
    public void testRemove() {
        Map map = createStrictMock(Map.class);
        expect(map.remove("key")).andReturn(true);
        expect(map.remove("key")).andReturn(null);
        replay(map);

        assertTrue(new MapBackedSet(map).remove("key"));
        assertFalse(new MapBackedSet(map).remove("key"));
        verify(map);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testAdd() {
        Map map = createStrictMock(Map.class);
        expect(map.put("key", true)).andReturn(null);
        expect(map.put("key", true)).andReturn(true);
        replay(map);

        assertTrue(new MapBackedSet(map).add("key"));
        assertFalse(new MapBackedSet(map).add("key"));
        verify(map);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testClear() {
        Map map = createStrictMock(Map.class);
        map.clear();
        replay(map);

        new MapBackedSet(map).clear();
        verify(map);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testIterator() {
        Map map = createStrictMock(Map.class);
        Set keySet = createStrictMock(Set.class);
        Iterator keySetIterator = createStrictMock(Iterator.class);

        expect(map.keySet()).andReturn(keySet);
        expect(keySet.iterator()).andReturn(keySetIterator);
        replay(map);
        replay(keySet);
        replay(keySetIterator);

        assertSame(keySetIterator, new MapBackedSet(map).iterator());

        verify(map);
        verify(keySet);
        verify(keySetIterator);
    }
}
