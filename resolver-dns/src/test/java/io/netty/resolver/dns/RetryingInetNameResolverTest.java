/*
 * Copyright 2017 The Netty Project
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
package io.netty.resolver.dns;

import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class RetryingInetNameResolverTest {

    @Test
    public void testRetryOnTimeout() {
        final DnsQuestion question = mock(DnsQuestion.class);
        testRetryOnTimeout(new DnsNameResolverTimeoutException(
                new InetSocketAddress(NetUtil.LOCALHOST, 0), question, "timeout"));
        verifyNoMoreInteractions(question);
    }

    @Test
    public void testRetryOnTransportError() {
        final DnsQuestion question = mock(DnsQuestion.class);
        testRetryOnTimeout(new DnsNameResolverException(
                new InetSocketAddress(NetUtil.LOCALHOST, 0), question, "I/O", new IOException("I/O")));
        verifyNoMoreInteractions(question);
    }

    @SuppressWarnings("unchecked")
    private static void testRetryOnTimeout(final DnsNameResolverException cause) {
        final int retries = 2;
        final String hostname = "netty.io";
        DefaultEventLoopGroup group = new DefaultEventLoopGroup(1);

        try {
            EventLoop loop = group.next();
            DnsNameResolver resolver = mock(DnsNameResolver.class);
            when(resolver.executor()).thenReturn(loop);

            when(resolver.resolve(eq(hostname), any(Promise.class))).then(new Answer<Future<InetAddress>>() {
                private int called;
                @Override
                public Future<InetAddress> answer(InvocationOnMock invocationOnMock) throws Throwable {
                    String host = invocationOnMock.getArgument(0);
                    Promise<InetAddress> promise = invocationOnMock.getArgument(1);
                    if (called++ == retries) {
                        promise.setSuccess(NetUtil.LOCALHOST);
                    } else {
                        UnknownHostException ex = new UnknownHostException(host);
                        ex.initCause(cause);
                        promise.setFailure(ex);
                    }
                    return promise;
                }
            });
            RetryingInetNameResolver retryingInetNameResolver = new RetryingInetNameResolver(resolver, retries);
            retryingInetNameResolver.resolve(hostname).syncUninterruptibly();

            verify(resolver, times(3)).resolve(eq(hostname), any(Promise.class));
        } finally {
            group.shutdownGracefully();
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testNotRetryOnPlainUnkownHostException() {
        final DnsQuestion question = mock(DnsQuestion.class);
        testRetryOnTimeout(new DnsNameResolverException(
                new InetSocketAddress(NetUtil.LOCALHOST, 0), question, "I/O", new IOException("I/O")));
        verifyNoMoreInteractions(question);

        final int retries = 2;
        final String hostname = "netty.io";
        DefaultEventLoopGroup group = new DefaultEventLoopGroup(1);

        try {
            EventLoop loop = group.next();
            DnsNameResolver resolver = mock(DnsNameResolver.class);
            when(resolver.executor()).thenReturn(loop);

            when(resolver.resolve(eq(hostname), any(Promise.class))).then(new Answer<Future<InetAddress>>() {
                @Override
                public Future<InetAddress> answer(InvocationOnMock invocationOnMock) throws Throwable {
                    String host = invocationOnMock.getArgument(0);
                    Promise<InetAddress> promise = invocationOnMock.getArgument(1);
                    promise.setFailure(new UnknownHostException(host));
                    return promise;
                }
            });
            RetryingInetNameResolver retryingInetNameResolver = new RetryingInetNameResolver(resolver, retries);
            Throwable cause = retryingInetNameResolver.resolve(hostname).awaitUninterruptibly().cause();
            assertTrue(cause instanceof UnknownHostException);
            verify(resolver, times(1)).resolve(eq(hostname), any(Promise.class));
        } finally {
            group.shutdownGracefully();
        }
    }
}
