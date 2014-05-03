/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import io.netty.handler.codec.http2.Http2PriorityTree.Priority;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link DefaultHttp2PriorityTree}.
 */
public class DefaultHttp2PriorityTreeTest {
    private DefaultHttp2PriorityTree<Object> tree;

    @Before
    public void setup() {
        tree = new DefaultHttp2PriorityTree<Object>();
    }

    @Test
    public void prioritizeShouldUseDefaults() {
        Object data = new Object();
        tree.prioritizeUsingDefaults(1, data);
        assertEquals(1, tree.root().numChildren());
        Priority<Object> p = tree.root().children().iterator().next();
        assertEquals(1, p.streamId());
        assertSame(data, p.data());
        assertEquals(DEFAULT_PRIORITY_WEIGHT, p.weight());
        assertEquals(0, p.parent().streamId());
        assertEquals(0, p.numChildren());
    }

    @Test
    public void prioritizeFromEmptyShouldSucceed() {
        Object data = new Object();
        tree.prioritize(1, 0, DEFAULT_PRIORITY_WEIGHT, false, data);
        assertEquals(1, tree.root().numChildren());
        Priority<Object> p = tree.root().getChild(1);
        assertNotNull(p);
        assertSame(data, p.data());
        assertEquals(DEFAULT_PRIORITY_WEIGHT, p.weight());
        assertEquals(0, p.parent().streamId());
        assertEquals(0, p.numChildren());
    }

    @Test
    public void reprioritizeWithNoChangeShouldDoNothing() {
        Object d1 = new Object();
        Object d2 = new Object();
        tree.prioritize(1, 0, DEFAULT_PRIORITY_WEIGHT, false, d1);
        tree.prioritize(1, 0, DEFAULT_PRIORITY_WEIGHT, false, d2);
        assertEquals(1, tree.root().numChildren());
        Priority<Object> p = tree.root().getChild(1);
        assertNotNull(p);
        assertSame(d2, p.data());
        assertEquals(DEFAULT_PRIORITY_WEIGHT, p.weight());
        assertEquals(0, p.parent().streamId());
        assertEquals(0, p.numChildren());
    }

    @Test
    public void insertExclusiveShouldAddNewLevel() {
        Object d1 = new Object();
        Object d2 = new Object();
        Object d3 = new Object();
        Object d4 = new Object();
        tree.prioritize(1, 0, DEFAULT_PRIORITY_WEIGHT, false, d1);
        tree.prioritize(2, 1, DEFAULT_PRIORITY_WEIGHT, false, d2);
        tree.prioritize(3, 1, DEFAULT_PRIORITY_WEIGHT, false, d3);
        tree.prioritize(4, 1, DEFAULT_PRIORITY_WEIGHT, true, d4);
        assertEquals(4, tree.size());

        // Level 0
        Priority<Object> p = tree.root();
        assertEquals(1, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 1
        p = p.getChild(1);
        assertNotNull(p);
        assertSame(d1, p.data());
        assertEquals(0, p.parent().streamId());
        assertEquals(1, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 2
        p = p.getChild(4);
        assertNotNull(p);
        assertSame(d4, p.data());
        assertEquals(1, p.parent().streamId());
        assertEquals(2, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 3
        p = p.getChild(2);
        assertNotNull(p);
        assertSame(d2, p.data());
        assertEquals(4, p.parent().streamId());
        assertEquals(0, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
        p = p.parent().getChild(3);
        assertNotNull(p);
        assertSame(d3, p.data());
        assertEquals(4, p.parent().streamId());
        assertEquals(0, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
    }

    @Test
    public void removeShouldRestructureTree() {
        Object d1 = new Object();
        Object d2 = new Object();
        Object d3 = new Object();
        Object d4 = new Object();
        tree.prioritize(1, 0, DEFAULT_PRIORITY_WEIGHT, false, d1);
        tree.prioritize(2, 1, DEFAULT_PRIORITY_WEIGHT, false, d2);
        tree.prioritize(3, 2, DEFAULT_PRIORITY_WEIGHT, false, d3);
        tree.prioritize(4, 2, DEFAULT_PRIORITY_WEIGHT, false, d4);
        tree.remove(2);

        // Level 0
        Priority<Object> p = tree.root();
        assertEquals(1, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 1
        p = p.getChild(1);
        assertNotNull(p);
        assertSame(d1, p.data());
        assertEquals(0, p.parent().streamId());
        assertEquals(2, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 2
        p = p.getChild(3);
        assertNotNull(p);
        assertSame(d3, p.data());
        assertEquals(1, p.parent().streamId());
        assertEquals(0, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
        p = p.parent().getChild(4);
        assertNotNull(p);
        assertSame(d4, p.data());
        assertEquals(1, p.parent().streamId());
        assertEquals(0, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
    }

    @Test
    public void circularDependencyShouldRestructureTree() {
        // Using example from http://tools.ietf.org/html/draft-ietf-httpbis-http2-12#section-5.3.3
        int a = 1;
        int b = 2;
        int c = 3;
        int d = 4;
        int e = 5;
        int f = 6;
        tree.prioritize(a, 0, DEFAULT_PRIORITY_WEIGHT, false, null);
        tree.prioritize(b, a, DEFAULT_PRIORITY_WEIGHT, false, null);
        tree.prioritize(c, a, DEFAULT_PRIORITY_WEIGHT, false, null);
        tree.prioritize(d, c, DEFAULT_PRIORITY_WEIGHT, false, null);
        tree.prioritize(e, c, DEFAULT_PRIORITY_WEIGHT, false, null);
        tree.prioritize(f, d, DEFAULT_PRIORITY_WEIGHT, false, null);
        assertEquals(6, tree.size());

        // Non-exclusive re-prioritization of a->d.
        tree.prioritize(a, d, DEFAULT_PRIORITY_WEIGHT, false, null);

        // Level 0
        Priority<Object> p = tree.root();
        assertEquals(1, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 1
        p = p.getChild(d);
        assertNotNull(p);
        assertEquals(2, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 2
        p = p.getChild(f);
        assertNotNull(p);
        assertEquals(0, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
        p = p.parent().getChild(a);
        assertNotNull(p);
        assertEquals(2, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 3
        p = p.getChild(b);
        assertNotNull(p);
        assertEquals(0, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
        p = p.parent().getChild(c);
        assertNotNull(p);
        assertEquals(1, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 4;
        p = p.getChild(e);
        assertNotNull(p);
        assertEquals(0, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
    }

    @Test
    public void circularDependencyWithExclusiveShouldRestructureTree() {
        // Using example from http://tools.ietf.org/html/draft-ietf-httpbis-http2-12#section-5.3.3
        // Although the expected output for the exclusive case has an error in the document. The
        // final dependency of C should be E (not F). This is fixed here.
        int a = 1;
        int b = 2;
        int c = 3;
        int d = 4;
        int e = 5;
        int f = 6;
        tree.prioritize(a, 0, DEFAULT_PRIORITY_WEIGHT, false, null);
        tree.prioritize(b, a, DEFAULT_PRIORITY_WEIGHT, false, null);
        tree.prioritize(c, a, DEFAULT_PRIORITY_WEIGHT, false, null);
        tree.prioritize(d, c, DEFAULT_PRIORITY_WEIGHT, false, null);
        tree.prioritize(e, c, DEFAULT_PRIORITY_WEIGHT, false, null);
        tree.prioritize(f, d, DEFAULT_PRIORITY_WEIGHT, false, null);
        assertEquals(6, tree.size());

        // Exclusive re-prioritization of a->d.
        tree.prioritize(a, d, DEFAULT_PRIORITY_WEIGHT, true, null);

        // Level 0
        Priority<Object> p = tree.root();
        assertEquals(1, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 1
        p = p.getChild(d);
        assertNotNull(p);
        assertEquals(1, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 2
        p = p.getChild(a);
        assertNotNull(p);
        assertEquals(3, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 3
        p = p.getChild(b);
        assertNotNull(p);
        assertEquals(0, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
        p = p.parent().getChild(f);
        assertNotNull(p);
        assertEquals(0, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
        p = p.parent().getChild(c);
        assertNotNull(p);
        assertEquals(1, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 4;
        p = p.getChild(e);
        assertNotNull(p);
        assertEquals(0, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
    }
}
