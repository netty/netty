/*
 * Copyright 2016  The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs.util;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.CountDownLatch;

public final class CountDownAnswer implements Answer<Void> {

    private final CountDownLatch countDownLatch;
    private final String methodName;

    public CountDownAnswer(final CountDownLatch countDownLatch, final String methodName) {
        this.countDownLatch = countDownLatch;
        this.methodName = methodName;
    }

    @Override
    public Void answer(final InvocationOnMock invocation) throws Throwable {
        if (methodName.equals(invocation.getMethod().getName())) {
            countDownLatch.countDown();
        }
        return null;
    }
}
