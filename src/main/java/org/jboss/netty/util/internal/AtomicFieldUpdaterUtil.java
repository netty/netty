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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
class AtomicFieldUpdaterUtil {

    private static final boolean AVAILABLE;

    static final class Node {
        volatile Node next;
        Node() {
            super();
        }
    }

    static {
        boolean available = false;
        try {
            AtomicReferenceFieldUpdater<Node, Node> tmp =
                AtomicReferenceFieldUpdater.newUpdater(
                        Node.class, Node.class, "next");

            // Test if AtomicReferenceFieldUpdater is really working.
            Node testNode = new Node();
            tmp.set(testNode, testNode);
            if (testNode.next != testNode) {
                // Not set as expected - fall back to the safe mode.
                throw new Exception();
            }
            available = true;
        } catch (Throwable t) {
            // Running in a restricted environment with a security manager.
        }
        AVAILABLE = available;
    }

    static <T, V> AtomicReferenceFieldUpdater<T,V> newRefUpdater(Class<T> tclass, Class<V> vclass, String fieldName) {
        if (AVAILABLE) {
            return AtomicReferenceFieldUpdater.newUpdater(tclass, vclass, fieldName);
        } else {
            return null;
        }
    }

    static <T> AtomicIntegerFieldUpdater<T> newIntUpdater(Class<T> tclass, String fieldName) {
        if (AVAILABLE) {
            return AtomicIntegerFieldUpdater.newUpdater(tclass, fieldName);
        } else {
            return null;
        }
    }

    static boolean isAvailable() {
        return AVAILABLE;
    }

    private AtomicFieldUpdaterUtil() {
        // Unused
    }
}
