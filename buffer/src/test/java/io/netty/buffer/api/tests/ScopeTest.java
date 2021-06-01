/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.buffer.api.tests;

import io.netty.buffer.api.Drop;
import io.netty.buffer.api.Owned;
import io.netty.buffer.api.internal.ResourceSupport;
import io.netty.buffer.api.Scope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScopeTest {
    @Test
    void scopeMustCloseContainedRecouresInReverseInsertOrder() {
        ArrayList<Integer> closeOrder = new ArrayList<>();
        try (Scope scope = new Scope()) {
            scope.add(new SomeResource(new OrderingDrop(1, closeOrder)));
            scope.add(new SomeResource(new OrderingDrop(2, closeOrder)));
            scope.add(new SomeResource(new OrderingDrop(3, closeOrder)));
        }
        var itr = closeOrder.iterator();
        assertTrue(itr.hasNext());
        assertEquals(3, (int) itr.next());
        assertTrue(itr.hasNext());
        assertEquals(2, (int) itr.next());
        assertTrue(itr.hasNext());
        assertEquals(1, (int) itr.next());
        assertFalse(itr.hasNext());
    }

    private static final class SomeResource extends ResourceSupport<SomeResource, SomeResource> {
        SomeResource(Drop<SomeResource> drop) {
            super(drop);
        }

        @Override
        protected RuntimeException createResourceClosedException() {
            return new IllegalStateException("This resource is closed: " + this);
        }

        @Override
        protected Owned<SomeResource> prepareSend() {
            return null;
        }
    }

    private static final class OrderingDrop implements Drop<SomeResource> {
        private final int order;
        private final ArrayList<Integer> list;

        private OrderingDrop(int order, ArrayList<Integer> list) {
            this.order = order;
            this.list = list;
        }

        @Override
        public void drop(SomeResource obj) {
            list.add(order);
        }
    }
}
