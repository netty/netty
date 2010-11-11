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
package org.jboss.netty.util;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.junit.Test;


/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2371 $, $Date: 2010-10-19 15:00:42 +0900 (Tue, 19 Oct 2010) $
 *
 */
public class MapBackedSetTest {

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testSize() {
        Map map = createStrictMock(Map.class);
        expect(map.size()).andReturn(0);
        replay(map);

        assertEquals(0, new MapBackedSet(map).size());
        verify(map);
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testContains() {
        Map map = createStrictMock(Map.class);
        expect(map.containsKey("key")).andReturn(true);
        replay(map);

        assertTrue(new MapBackedSet(map).contains("key"));
        verify(map);
    }


    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
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
    @SuppressWarnings({"unchecked", "rawtypes"})
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
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testClear() {
        Map map = createStrictMock(Map.class);
        map.clear();
        replay(map);

        new MapBackedSet(map).clear();
        verify(map);
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
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
