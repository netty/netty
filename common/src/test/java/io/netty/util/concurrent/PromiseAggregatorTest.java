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

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

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
        Promise<Void> p = mock(Promise.class);
        PromiseAggregator<Void, Future<Void>> a =
                new PromiseAggregator<Void, Future<Void>>(p);
        expectedException.expect(NullPointerException.class);
        a.add((Promise<Void>[]) null);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSuccessfulNoPending() throws Exception {
        Promise<Void> p = mock(Promise.class);
        PromiseAggregator<Void, Future<Void>> a =
                new PromiseAggregator<Void, Future<Void>>(p);

        Future<Void> future = mock(Future.class);
        when(p.setSuccess(null)).thenReturn(p);

        a.add();
        a.operationComplete(future);
        verifyNoMoreInteractions(future);
        verify(p).setSuccess(null);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSuccessfulPending() throws Exception {
        Promise<Void> p = mock(Promise.class);
        PromiseAggregator<Void, Future<Void>> a =
                new PromiseAggregator<Void, Future<Void>>(p);
        Promise<Void> p1 = mock(Promise.class);
        Promise<Void> p2 = mock(Promise.class);

        when(p1.addListener(a)).thenReturn(p1);
        when(p2.addListener(a)).thenReturn(p2);
        when(p1.isSuccess()).thenReturn(true);
        when(p2.isSuccess()).thenReturn(true);
        when(p.setSuccess(null)).thenReturn(p);

        assertThat(a.add(p1, null, p2), is(a));
        a.operationComplete(p1);
        a.operationComplete(p2);

        verify(p1).addListener(a);
        verify(p2).addListener(a);
        verify(p1).isSuccess();
        verify(p2).isSuccess();
        verify(p).setSuccess(null);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testFailedFutureFailPending() throws Exception {
        Promise<Void> p = mock(Promise.class);
        PromiseAggregator<Void, Future<Void>> a =
                new PromiseAggregator<Void, Future<Void>>(p);
        Promise<Void> p1 = mock(Promise.class);
        Promise<Void> p2 = mock(Promise.class);
        Throwable t = mock(Throwable.class);

        when(p1.addListener(a)).thenReturn(p1);
        when(p2.addListener(a)).thenReturn(p2);
        when(p1.isSuccess()).thenReturn(false);
        when(p1.cause()).thenReturn(t);
        when(p.setFailure(t)).thenReturn(p);
        when(p2.setFailure(t)).thenReturn(p2);

        a.add(p1, p2);
        a.operationComplete(p1);

        verify(p1).addListener(a);
        verify(p2).addListener(a);
        verify(p1).cause();
        verify(p).setFailure(t);
        verify(p2).setFailure(t);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testFailedFutureNoFailPending() throws Exception {
        Promise<Void> p = mock(Promise.class);
        PromiseAggregator<Void, Future<Void>> a =
                new PromiseAggregator<Void, Future<Void>>(p, false);
        Promise<Void> p1 = mock(Promise.class);
        Promise<Void> p2 = mock(Promise.class);
        Throwable t = mock(Throwable.class);

        when(p1.addListener(a)).thenReturn(p1);
        when(p2.addListener(a)).thenReturn(p2);
        when(p1.isSuccess()).thenReturn(false);
        when(p1.cause()).thenReturn(t);
        when(p.setFailure(t)).thenReturn(p);

        a.add(p1, p2);
        a.operationComplete(p1);

        verify(p1).addListener(a);
        verify(p2).addListener(a);
        verify(p1).isSuccess();
        verify(p1).cause();
        verify(p).setFailure(t);
    }
}
