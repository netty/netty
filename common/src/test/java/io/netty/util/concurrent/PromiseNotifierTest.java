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

package io.netty.util.concurrent;

import static org.easymock.EasyMock.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PromiseNotifierTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testNullPromisesArray() {
        expectedException.expect(NullPointerException.class);
        new PromiseNotifier<Void, Future<Void>>((Promise<Void>[]) null);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testNullPromiseInArray() {
        expectedException.expect(IllegalArgumentException.class);
        new PromiseNotifier<Void, Future<Void>>((Promise<Void>) null);
    }

    @Test
    public void testListenerSuccess() throws Exception {
        @SuppressWarnings("unchecked")
        Promise<Void> p1 = createStrictMock(Promise.class);
        @SuppressWarnings("unchecked")
        Promise<Void> p2 = createStrictMock(Promise.class);

        @SuppressWarnings("unchecked")
        PromiseNotifier<Void, Future<Void>> notifier =
                new PromiseNotifier<Void, Future<Void>>(p1, p2);

        @SuppressWarnings("unchecked")
        Future<Void> future = createStrictMock(Future.class);
        expect(future.isSuccess()).andReturn(true);
        expect(future.get()).andReturn(null);
        expect(p1.trySuccess(null)).andReturn(true);
        expect(p2.trySuccess(null)).andReturn(true);
        replay(p1, p2, future);

        notifier.operationComplete(future);
        verify(p1, p2);
    }

    @Test
    public void testListenerFailure() throws Exception {
        @SuppressWarnings("unchecked")
        Promise<Void> p1 = createStrictMock(Promise.class);
        @SuppressWarnings("unchecked")
        Promise<Void> p2 = createStrictMock(Promise.class);

        @SuppressWarnings("unchecked")
        PromiseNotifier<Void, Future<Void>> notifier =
                new PromiseNotifier<Void, Future<Void>>(p1, p2);

        @SuppressWarnings("unchecked")
        Future<Void> future = createStrictMock(Future.class);
        Throwable t = createStrictMock(Throwable.class);
        expect(future.isSuccess()).andReturn(false);
        expect(future.isCancelled()).andReturn(false);
        expect(future.cause()).andReturn(t);
        expect(p1.tryFailure(t)).andReturn(true);
        expect(p2.tryFailure(t)).andReturn(true);
        replay(p1, p2, future);

        notifier.operationComplete(future);
        verify(p1, p2);
    }

}
