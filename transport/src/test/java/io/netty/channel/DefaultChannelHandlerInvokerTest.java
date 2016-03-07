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
package io.netty.channel;

import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DefaultChannelHandlerInvokerTest {
    @Mock
    private ReferenceCounted msg;
    @Mock
    private ChannelHandlerContext ctx;
    @Mock
    private ChannelPromise promise;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void writeWithInvalidPromiseStillReleasesMessage() {
        when(promise.isDone()).thenReturn(true);
        DefaultChannelHandlerInvoker invoker = new DefaultChannelHandlerInvoker(ImmediateEventExecutor.INSTANCE);
        try {
            invoker.invokeWrite(ctx, msg, promise);
        } catch (IllegalArgumentException e) {
            verify(msg).release();
            return;
        }
        fail();
    }

    @Test
    public void writeWithNullPromiseStillReleasesMessage() {
        when(promise.isDone()).thenReturn(true);
        DefaultChannelHandlerInvoker invoker = new DefaultChannelHandlerInvoker(ImmediateEventExecutor.INSTANCE);
        try {
            invoker.invokeWrite(ctx, msg, null);
        } catch (NullPointerException e) {
            verify(msg).release();
            return;
        }
        fail();
    }
}
