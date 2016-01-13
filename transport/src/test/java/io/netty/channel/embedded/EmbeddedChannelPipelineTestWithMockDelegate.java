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
 */package io.netty.channel.embedded;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerInvoker;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel.LastInboundHandler;
import io.netty.util.concurrent.EventExecutorGroup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.net.InetSocketAddress;

import static io.netty.channel.embedded.EmbeddedChannel.LAST_HANDLER_NAME;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class EmbeddedChannelPipelineTestWithMockDelegate {

    private static final String HANDLER_NAME = "handler";
    private static final String HANDLER_NAME_DUMMY = "dummy-handler";

    @Mock
    private ChannelPipeline delegate;
    @Mock
    private EventExecutorGroup mockGroup;
    @Mock
    private ChannelHandler mockHandler;
    @Mock
    private ChannelHandlerInvoker mockInvoker;
    @Mock
    private ChannelPromise mockPromise;

    @Test(timeout = 60000)
    public void testAddFirst() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.addFirst(HANDLER_NAME, mockHandler);
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).addFirst(HANDLER_NAME, mockHandler);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testAddFirstWithGroup() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.addFirst(mockGroup, HANDLER_NAME, mockHandler);
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).addFirst(mockGroup, HANDLER_NAME, mockHandler);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testAddFirstWithInvoker() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.addFirst(mockInvoker, HANDLER_NAME, mockHandler);
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).addFirst(mockInvoker, HANDLER_NAME, mockHandler);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testAddLast() throws Exception {
        Mockito.when(delegate.get(LAST_HANDLER_NAME)).thenReturn(mockHandler);
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.addLast(HANDLER_NAME, mockHandler);
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).get(LAST_HANDLER_NAME);
        Mockito.verify(delegate).addBefore(LAST_HANDLER_NAME, HANDLER_NAME,  mockHandler);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testAddLastWithGroup() throws Exception {
        Mockito.when(delegate.get(LAST_HANDLER_NAME)).thenReturn(mockHandler);
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.addLast(mockGroup, HANDLER_NAME, mockHandler);
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).get(LAST_HANDLER_NAME);
        Mockito.verify(delegate).addBefore(mockGroup, LAST_HANDLER_NAME, HANDLER_NAME,  mockHandler);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testAddLastWithInvoker() throws Exception {
        Mockito.when(delegate.get(LAST_HANDLER_NAME)).thenReturn(mockHandler);
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.addLast(mockInvoker, HANDLER_NAME, mockHandler);
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).get(LAST_HANDLER_NAME);
        Mockito.verify(delegate).addBefore(mockInvoker, LAST_HANDLER_NAME, HANDLER_NAME,  mockHandler);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testAddBefore() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.addBefore(HANDLER_NAME_DUMMY, HANDLER_NAME, mockHandler);
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).addBefore(HANDLER_NAME_DUMMY, HANDLER_NAME, mockHandler);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testAddBeforeWithGroup() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.addBefore(mockGroup, HANDLER_NAME_DUMMY, HANDLER_NAME, mockHandler);
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).addBefore(mockGroup, HANDLER_NAME_DUMMY, HANDLER_NAME, mockHandler);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testAddBeforeWithInvoker() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.addBefore(mockInvoker, HANDLER_NAME_DUMMY, HANDLER_NAME, mockHandler);
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).addBefore(mockInvoker, HANDLER_NAME_DUMMY, HANDLER_NAME, mockHandler);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testAddAfter() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.addAfter(HANDLER_NAME_DUMMY, HANDLER_NAME, mockHandler);
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).addAfter(HANDLER_NAME_DUMMY, HANDLER_NAME, mockHandler);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testAddAfterWithGroup() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.addAfter(mockGroup, HANDLER_NAME_DUMMY, HANDLER_NAME, mockHandler);
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).addAfter(mockGroup, HANDLER_NAME_DUMMY, HANDLER_NAME, mockHandler);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testAddAfterWithInvoker() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.addAfter(mockInvoker, HANDLER_NAME_DUMMY, HANDLER_NAME, mockHandler);
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).addAfter(mockInvoker, HANDLER_NAME_DUMMY, HANDLER_NAME, mockHandler);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testAddFirstMulti() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.addFirst(mockHandler, mockHandler);
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).addFirst(mockHandler, mockHandler);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testAddFirstMultiWithGroup() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.addFirst(mockGroup, mockHandler, mockHandler);
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).addFirst(mockGroup, mockHandler, mockHandler);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testAddFirstMultiWithInvoker() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.addFirst(mockInvoker, mockHandler, mockHandler);
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).addFirst(mockInvoker, mockHandler, mockHandler);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testAddLastMulti() throws Exception {
        Mockito.when(delegate.get(LAST_HANDLER_NAME)).thenReturn(mockHandler);
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.addLast(mockHandler, mockHandler);
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate, times(2)).addBefore(LAST_HANDLER_NAME, null, mockHandler);
        Mockito.verify(delegate).get(LAST_HANDLER_NAME);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testAddLastMultiWithGroup() throws Exception {
        Mockito.when(delegate.get(LAST_HANDLER_NAME)).thenReturn(mockHandler);
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.addLast(mockGroup, mockHandler, mockHandler);
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).get(LAST_HANDLER_NAME);
        Mockito.verify(delegate, times(2)).addBefore(mockGroup, LAST_HANDLER_NAME, null, mockHandler);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testAddLastMultiWithInvoker() throws Exception {
        Mockito.when(delegate.get(LAST_HANDLER_NAME)).thenReturn(mockHandler);
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.addLast(mockInvoker, mockHandler, mockHandler);
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).get(LAST_HANDLER_NAME);
        Mockito.verify(delegate, times(2)).addBefore(mockInvoker, LAST_HANDLER_NAME, null, mockHandler);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testRemove() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.remove(mockHandler);
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).remove(mockHandler);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testRemoveByName() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.remove(HANDLER_NAME);
        Mockito.verify(delegate).remove(HANDLER_NAME);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testRemoveByClass() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.remove(mockHandler.getClass());
        Mockito.verify(delegate).remove(mockHandler.getClass());
    }

    @Test(timeout = 60000)
    public void testRemoveFirst() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.removeFirst();
        Mockito.verify(delegate).removeFirst();
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testRemoveLast() throws Exception {
        EmbeddedChannel ec = new EmbeddedChannel();
        LastInboundHandler lih = ec.new LastInboundHandler();

        Mockito.when(delegate.last()).thenReturn(lih);

        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelHandler removed = ecp.removeLast();
        assertThat("Last Inbound Handler removed.", removed, is(nullValue()));
        Mockito.verify(delegate, times(2)).removeLast();
        Mockito.verify(delegate).addLast(LAST_HANDLER_NAME, lih);
    }

    @Test(timeout = 60000)
    public void testReplace() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.replace(mockHandler, HANDLER_NAME, mockHandler);
        Mockito.verify(delegate).replace(mockHandler, HANDLER_NAME, mockHandler);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testReplaceByName() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.replace(HANDLER_NAME, HANDLER_NAME, mockHandler);
        Mockito.verify(delegate).replace(HANDLER_NAME, HANDLER_NAME, mockHandler);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testReplaceByClass() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.replace(mockHandler.getClass(), HANDLER_NAME, mockHandler);
        Mockito.verify(delegate).replace(mockHandler.getClass(), HANDLER_NAME, mockHandler);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testFirst() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.first();
        Mockito.verify(delegate).first();
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testFirstContext() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.firstContext();
        Mockito.verify(delegate).firstContext();
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testLast() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.last();
        Mockito.verify(delegate).last();
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testLastContext() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.lastContext();
        Mockito.verify(delegate).lastContext();
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testGetByName() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.get(HANDLER_NAME);
        Mockito.verify(delegate).get(HANDLER_NAME);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testGetByClass() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.get(mockHandler.getClass());
        Mockito.verify(delegate).get(mockHandler.getClass());
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testContextByName() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.context(HANDLER_NAME);
        Mockito.verify(delegate).context(HANDLER_NAME);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testContextByClass() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.context(mockHandler.getClass());
        Mockito.verify(delegate).context(mockHandler.getClass());
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testContextByHandler() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.context(mockHandler);
        Mockito.verify(delegate).context(mockHandler);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testChannel() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.channel();
        Mockito.verify(delegate).channel();
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testNames() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.names();
        Mockito.verify(delegate).names();
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testToMap() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.toMap();
        Mockito.verify(delegate).toMap();
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testFireChannelRegistered() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.fireChannelRegistered();
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).fireChannelRegistered();
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testFireChannelUnregistered() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.fireChannelUnregistered();
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).fireChannelUnregistered();
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testFireChannelActive() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.fireChannelActive();
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).fireChannelActive();
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testFireChannelInactive() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.fireChannelInactive();
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).fireChannelInactive();
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testFireExceptionCaught() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        Throwable t = new IllegalStateException();
        ChannelPipeline cp = ecp.fireExceptionCaught(t);
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).fireExceptionCaught(t);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testFireUserEventTriggered() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        Throwable t = new IllegalStateException();
        ChannelPipeline cp = ecp.fireUserEventTriggered(t);
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).fireUserEventTriggered(t);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testFireChannelRead() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        Throwable t = new IllegalStateException();
        ChannelPipeline cp = ecp.fireChannelRead(t);
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).fireChannelRead(t);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testFireChannelReadComplete() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.fireChannelReadComplete();
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).fireChannelReadComplete();
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testFireChannelWritabilityChanged() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ChannelPipeline cp = ecp.fireChannelWritabilityChanged();
        assertThat("Unexpected returned pipeline instance.", cp, is(ecp));
        Mockito.verify(delegate).fireChannelWritabilityChanged();
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testBind() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        InetSocketAddress address = new InetSocketAddress(0);
        ecp.bind(address);
        Mockito.verify(delegate).bind(address);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testConnect() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        InetSocketAddress address = new InetSocketAddress(0);
        ecp.connect(address);
        Mockito.verify(delegate).connect(address);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testConnectWithLocalAndRemote() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        InetSocketAddress address = new InetSocketAddress(0);
        ecp.connect(address, address);
        Mockito.verify(delegate).connect(address, address);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testDisconnect() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.disconnect();
        Mockito.verify(delegate).disconnect();
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testClose() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.close();
        Mockito.verify(delegate).close();
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testDeregister() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.deregister();
        Mockito.verify(delegate).deregister();
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testBindWithPromise() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        InetSocketAddress address = new InetSocketAddress(0);
        ecp.bind(address, mockPromise);
        Mockito.verify(delegate).bind(address, mockPromise);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testConnectWithPromise() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        InetSocketAddress address = new InetSocketAddress(0);
        ecp.connect(address, mockPromise);
        Mockito.verify(delegate).connect(address, mockPromise);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testConnectWithRemoteAndPromise() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        InetSocketAddress address = new InetSocketAddress(0);
        ecp.connect(address, address, mockPromise);
        Mockito.verify(delegate).connect(address, address, mockPromise);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testDisconnectWithPromise() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.disconnect(mockPromise);
        Mockito.verify(delegate).disconnect(mockPromise);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testCloseWithPromise() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.close(mockPromise);
        Mockito.verify(delegate).close(mockPromise);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testDeregisterWithPromise() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.deregister(mockPromise);
        Mockito.verify(delegate).deregister(mockPromise);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testRead() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.read();
        Mockito.verify(delegate).read();
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testWrite() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.write("");
        Mockito.verify(delegate).write("");
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testWriteWithPromise() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.write("", mockPromise);
        Mockito.verify(delegate).write("", mockPromise);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testFlush() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.flush();
        Mockito.verify(delegate).flush();
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testWriteAndFlush() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.writeAndFlush("");
        Mockito.verify(delegate).writeAndFlush("");
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testWriteAndFlushWithPromise() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.writeAndFlush("", mockPromise);
        Mockito.verify(delegate).writeAndFlush("", mockPromise);
        Mockito.verifyNoMoreInteractions(delegate);
    }

    @Test(timeout = 60000)
    public void testIterator() throws Exception {
        ChannelPipeline ecp = new EmbeddedChannelPipeline(delegate);
        ecp.iterator();
        Mockito.verify(delegate).iterator();
        Mockito.verifyNoMoreInteractions(delegate);
    }
}
