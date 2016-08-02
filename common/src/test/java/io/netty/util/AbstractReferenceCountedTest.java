/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AbstractReferenceCountedTest {

    @Test(expected = IllegalReferenceCountException.class)
    public void testRetainOverflow() {
        AbstractReferenceCounted referenceCounted = newReferenceCounted();
        referenceCounted.setRefCnt(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, referenceCounted.refCnt());
        referenceCounted.retain();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testRetainOverflow2() {
        AbstractReferenceCounted referenceCounted = newReferenceCounted();
        assertEquals(1, referenceCounted.refCnt());
        referenceCounted.retain(Integer.MAX_VALUE);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReleaseOverflow() {
        AbstractReferenceCounted referenceCounted = newReferenceCounted();
        referenceCounted.setRefCnt(0);
        assertEquals(0, referenceCounted.refCnt());
        referenceCounted.release(Integer.MAX_VALUE);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testRetainResurrect() {
        AbstractReferenceCounted referenceCounted = newReferenceCounted();
        assertTrue(referenceCounted.release());
        assertEquals(0, referenceCounted.refCnt());
        referenceCounted.retain();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testRetainResurrect2() {
        AbstractReferenceCounted referenceCounted = newReferenceCounted();
        assertTrue(referenceCounted.release());
        assertEquals(0, referenceCounted.refCnt());
        referenceCounted.retain(2);
    }

    private static AbstractReferenceCounted newReferenceCounted() {
        return new AbstractReferenceCounted() {
            @Override
            protected void deallocate() {
                // NOOP
            }

            @Override
            public ReferenceCounted touch(Object hint) {
                return this;
            }
        };
    }
}
