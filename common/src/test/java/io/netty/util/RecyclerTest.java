/*
* Copyright 2014 The Netty Project
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

import org.junit.Assert;
import org.junit.Test;

public class RecyclerTest {

    @Test(expected = IllegalStateException.class)
    public void testMultipleRecycle() {
        RecyclableObject object = RecyclableObject.newInstance();
        object.recycle();
        object.recycle();
    }

    @Test
    public void testRecycle() {
        RecyclableObject object = RecyclableObject.newInstance();
        object.recycle();
        RecyclableObject object2 = RecyclableObject.newInstance();
        Assert.assertSame(object, object2);
        object2.recycle();
    }

    static final class RecyclableObject {

        private static final Recycler<RecyclableObject> RECYCLER = new Recycler<RecyclableObject>() {
            @Override
            protected RecyclableObject newObject(Handle handle) {
                return new RecyclableObject(handle);
            }
        };

        private final Recycler.Handle handle;

        private RecyclableObject(Recycler.Handle handle) {
            this.handle = handle;
        }

        public static RecyclableObject newInstance() {
            return RECYCLER.get();
        }

        public void recycle() {
            RECYCLER.recycle(this, handle);
        }
    }
}
