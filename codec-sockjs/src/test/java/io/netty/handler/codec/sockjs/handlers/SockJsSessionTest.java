/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.sockjs.handlers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import io.netty.handler.codec.sockjs.SockJsSessionContext;
import io.netty.handler.codec.sockjs.SockJsService;
import io.netty.handler.codec.sockjs.handlers.SockJsSession.States;

import org.junit.Test;

public class SockJsSessionTest {

    @Test
    public void setState() throws Exception {
        final SockJsService service = mock(SockJsService.class);
        final SockJsSession session = new SockJsSession("123", service);
        session.setState(States.OPEN);
        assertThat(session.getState(), is(States.OPEN));
    }

    @Test
    public void onOpen() throws Exception {
        final SockJsService service = mock(SockJsService.class);
        final SockJsSession sockJSSession = new SockJsSession("123", service);
        final SockJsSessionContext session = mock(SockJsSessionContext.class);
        sockJSSession.onOpen(session);
        verify(service).onOpen(session);
        assertThat(sockJSSession.getState(), is(States.OPEN));
    }

    @Test
    public void onMessage() throws Exception {
        final SockJsService service = mock(SockJsService.class);
        final SockJsSession sockJSSession = new SockJsSession("123", service);
        sockJSSession.onMessage("testing");
        verify(service).onMessage("testing");
    }

    @Test
    public void onClose() throws Exception {
        final SockJsService service = mock(SockJsService.class);
        final SockJsSession sockJSSession = new SockJsSession("123", service);
        sockJSSession.onClose();
        verify(service).onClose();
    }

    @Test
    public void addMessage() throws Exception {
        final SockJsService service = mock(SockJsService.class);
        final SockJsSession sockJSSession = new SockJsSession("123", service);
        sockJSSession.addMessage("hello");
        assertThat(sockJSSession.getAllMessages().length, is(1));
    }

    @Test
    public void addMessages() throws Exception {
        final SockJsService service = mock(SockJsService.class);
        final SockJsSession sockJSSession = new SockJsSession("123", service);
        sockJSSession.addMessages(new String[]{"hello", "world"});
        assertThat(sockJSSession.getAllMessages().length, is(2));
    }

    @Test
    public void clearMessage() throws Exception {
        final SockJsService service = mock(SockJsService.class);
        final SockJsSession sockJSSession = new SockJsSession("123", service);
        sockJSSession.addMessage("hello");
        sockJSSession.clearMessagees();
        assertThat(sockJSSession.getAllMessages().length, is(0));
    }

}
