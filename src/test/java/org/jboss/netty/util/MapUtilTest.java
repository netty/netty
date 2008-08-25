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

import static org.junit.Assert.*;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.jboss.netty.logging.InternalLoggerFactory;
import org.junit.BeforeClass;
import org.junit.Test;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class MapUtilTest {

    @Test
    public void shouldReturnTrueIfLinkedHashMap() {
        assertTrue(MapUtil.isOrderedMap(new LinkedHashMap<String, String>()));
    }

    @Test
    public void shouldReturnTrueIfMapImplementsOrderedMap() {
        assertTrue(MapUtil.isOrderedMap(new DummyOrderedMap<String, String>()));
    }

    @Test
    public void shouldReturnFalseIfMapHasNoDefaultConstructor() {
        assertFalse(MapUtil.isOrderedMap(
                new MapWithoutDefaultConstructor<String, String>(
                        new HashMap<String, String>())));
    }

    @Test
    public void shouldReturnFalseIfMapIsNotOrdered() {
        assertFalse(MapUtil.isOrderedMap(new HashMap<String, String>()));
    }

    @Test
    public void shouldReturnTrueIfMapIsOrdered() {
        assertTrue(MapUtil.isOrderedMap(new UnknownOrderedMap<String, String>()));
    }

    @BeforeClass
    public static void setUp() {
        InternalLoggerFactory.setDefaultFactory(new SilentLoggerFactory());
    }

    interface OrderedMap {
        // A tag interface
    }

    static class DummyOrderedMap<K,V> extends AbstractMap<K, V> implements OrderedMap {

        private final Map<K, V> map = new HashMap<K, V>();

        @Override
        public Set<Entry<K, V>> entrySet() {
            return map.entrySet();
        }
    }

    static class MapWithoutDefaultConstructor<K, V> extends AbstractMap<K, V> {
        private final Map<K, V> map;

        MapWithoutDefaultConstructor(Map<K, V> map) {
            this.map = map;
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            return map.entrySet();
        }
    }

    static class UnknownOrderedMap<K,V> extends AbstractMap<K, V> {

        private final Map<K, V> map = new LinkedHashMap<K, V>();

        @Override
        public boolean containsKey(Object key) {
            return map.containsKey(key);
        }

        @Override
        public int size() {
            return map.size();
        }

        @Override
        public V put(K key, V value) {
            return map.put(key, value);
        }

        @Override
        public Set<K> keySet() {
            return map.keySet();
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            return map.entrySet();
        }
    }
}
