/*
 * Copyright 2014 The Netty Project
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

package io.netty.util.concurrent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class PromiseNotifierTest {

    @Test
    public void testNullPromisesArray() {
        assertThrows(NullPointerException.class, new Executable() {
            @Override
            public void execute() {
                new PromiseNotifier<Void, Future<Void>>((Promise<Void>[]) null);
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testNullPromiseInArray() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                new PromiseNotifier<Void, Future<Void>>((Promise<Void>) null);
            }
        });
    }

    @Test
    public void testListenerSuccess() throws Exception {
        @SuppressWarnings("unchecked")
        Promise<Void> p1 = mock(Promise.class);
        @SuppressWarnings("unchecked")
        Promise<Void> p2 = mock(Promise.class);

        @SuppressWarnings("unchecked")
        PromiseNotifier<Void, Future<Void>> notifier =
                new PromiseNotifier<Void, Future<Void>>(p1, p2);

        @SuppressWarnings("unchecked")
        Future<Void> future = mock(Future.class);
        when(future.isSuccess()).thenReturn(true);
        when(future.get()).thenReturn(null);
        when(p1.trySuccess(null)).thenReturn(true);
        when(p2.trySuccess(null)).thenReturn(true);

        notifier.operationComplete(future);
        verify(p1).trySuccess(null);
        verify(p2).trySuccess(null);
    }

    @Test
    public void testListenerFailure() throws Exception {
        @SuppressWarnings("unchecked")
        Promise<Void> p1 = mock(Promise.class);
        @SuppressWarnings("unchecked")
        Promise<Void> p2 = mock(Promise.class);

        @SuppressWarnings("unchecked")
        PromiseNotifier<Void, Future<Void>> notifier =
                new PromiseNotifier<Void, Future<Void>>(p1, p2);

        @SuppressWarnings("unchecked")
        Future<Void> future = mock(Future.class);
        Throwable t = mock(Throwable.class);
        when(future.isSuccess()).thenReturn(false);
        when(future.isCancelled()).thenReturn(false);
        when(future.cause()).thenReturn(t);
        when(p1.tryFailure(t)).thenReturn(true);
        when(p2.tryFailure(t)).thenReturn(true);

        notifier.operationComplete(future);
        verify(p1).tryFailure(t);
        verify(p2).tryFailure(t);
    }

}
