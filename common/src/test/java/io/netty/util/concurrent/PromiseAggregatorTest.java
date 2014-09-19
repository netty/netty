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
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PromiseAggregatorTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testNullAggregatePromise() {
        expectedException.expect(NullPointerException.class);
        new PromiseAggregator<Void, Future<Void>>(null);
    }

    @Test
    public void testAddNullFuture() {
        @SuppressWarnings("unchecked")
        Promise<Void> p = createStrictMock(Promise.class);
        PromiseAggregator<Void, Future<Void>> a =
                new PromiseAggregator<Void, Future<Void>>(p);
        expectedException.expect(NullPointerException.class);
        a.add((Promise<Void>[]) null);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSucessfulNoPending() throws Exception {
        Promise<Void> p = createStrictMock(Promise.class);
        PromiseAggregator<Void, Future<Void>> a =
                new PromiseAggregator<Void, Future<Void>>(p);

        Future<Void> future = createStrictMock(Future.class);
        expect(p.setSuccess(null)).andReturn(p);
        replay(future, p);

        a.add();
        a.operationComplete(future);
        verify(future, p);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSuccessfulPending() throws Exception {
        Promise<Void> p = createStrictMock(Promise.class);
        PromiseAggregator<Void, Future<Void>> a =
                new PromiseAggregator<Void, Future<Void>>(p);
        Promise<Void> p1 = createStrictMock(Promise.class);
        Promise<Void> p2 = createStrictMock(Promise.class);

        expect(p1.addListener(a)).andReturn(p1);
        expect(p2.addListener(a)).andReturn(p2);
        expect(p1.isSuccess()).andReturn(true);
        expect(p2.isSuccess()).andReturn(true);
        expect(p.setSuccess(null)).andReturn(p);
        replay(p1, p2, p);

        assertThat(a.add(p1, null, p2), is(a));
        a.operationComplete(p1);
        a.operationComplete(p2);

        verify(p1, p2, p);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testFailedFutureFailPending() throws Exception {
        Promise<Void> p = createStrictMock(Promise.class);
        PromiseAggregator<Void, Future<Void>> a =
                new PromiseAggregator<Void, Future<Void>>(p);
        Promise<Void> p1 = createStrictMock(Promise.class);
        Promise<Void> p2 = createStrictMock(Promise.class);
        Throwable t = createStrictMock(Throwable.class);

        expect(p1.addListener(a)).andReturn(p1);
        expect(p2.addListener(a)).andReturn(p2);
        expect(p1.isSuccess()).andReturn(false);
        expect(p1.cause()).andReturn(t);
        expect(p.setFailure(t)).andReturn(p);
        expect(p2.setFailure(t)).andReturn(p2);
        replay(p1, p2, p);

        a.add(p1, p2);
        a.operationComplete(p1);
        verify(p1, p2, p);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testFailedFutureNoFailPending() throws Exception {
        Promise<Void> p = createStrictMock(Promise.class);
        PromiseAggregator<Void, Future<Void>> a =
                new PromiseAggregator<Void, Future<Void>>(p, false);
        Promise<Void> p1 = createStrictMock(Promise.class);
        Promise<Void> p2 = createStrictMock(Promise.class);
        Throwable t = createStrictMock(Throwable.class);

        expect(p1.addListener(a)).andReturn(p1);
        expect(p2.addListener(a)).andReturn(p2);
        expect(p1.isSuccess()).andReturn(false);
        expect(p1.cause()).andReturn(t);
        expect(p.setFailure(t)).andReturn(p);
        replay(p1, p2, p);

        a.add(p1, p2);
        a.operationComplete(p1);
        verify(p1, p2, p);
    }

}
