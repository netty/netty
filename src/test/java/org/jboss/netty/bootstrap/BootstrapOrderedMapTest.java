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

import static org.junit.Assert.*;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Test;


/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 */
public class BootstrapOrderedMapTest {

    @Test
    public void shouldReturnTrueIfLinkedHashMap() {
        assertTrue(Bootstrap.isOrderedMap(new LinkedHashMap<String, String>()));
    }

    @Test
    public void shouldReturnTrueIfMapImplementsOrderedMap() {
        assertTrue(Bootstrap.isOrderedMap(new DummyOrderedMap<String, String>()));
    }

    @Test
    public void shouldReturnFalseIfMapHasNoDefaultConstructor() {
        assertFalse(Bootstrap.isOrderedMap(
                new MapWithoutDefaultConstructor<String, String>(
                        new HashMap<String, String>())));
    }

    @Test
    public void shouldReturnFalseIfMapIsNotOrdered() {
        assertFalse(Bootstrap.isOrderedMap(new HashMap<String, String>()));
    }

    @Test
    public void shouldReturnTrueIfMapIsOrdered() {
        assertTrue(Bootstrap.isOrderedMap(new UnknownOrderedMap<String, String>()));
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
