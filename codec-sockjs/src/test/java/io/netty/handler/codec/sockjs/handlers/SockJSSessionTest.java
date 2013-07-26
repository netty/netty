/*
 * Copyright 2012 The Netty Project
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
import io.netty.handler.codec.sockjs.SessionContext;
import io.netty.handler.codec.sockjs.SockJSService;
import io.netty.handler.codec.sockjs.handlers.SockJSSession.States;

import org.junit.Test;

public class SockJSSessionTest {

    @Test
    public void setState() throws Exception {
        final SockJSService service = mock(SockJSService.class);
        final SockJSSession session = new SockJSSession("123", service);
        session.setState(States.OPEN);
        assertThat(session.getState(), is(States.OPEN));
    }

    @Test
    public void onOpen() throws Exception {
        final SockJSService service = mock(SockJSService.class);
        final SockJSSession sockJSSession = new SockJSSession("123", service);
        final SessionContext session = mock(SessionContext.class);
        sockJSSession.onOpen(session);
        verify(service).onOpen(session);
        assertThat(sockJSSession.getState(), is(States.OPEN));
    }

    @Test
    public void onMessage() throws Exception {
        final SockJSService service = mock(SockJSService.class);
        final SockJSSession sockJSSession = new SockJSSession("123", service);
        sockJSSession.onMessage("testing");
        verify(service).onMessage("testing");
    }

    @Test
    public void onClose() throws Exception {
        final SockJSService service = mock(SockJSService.class);
        final SockJSSession sockJSSession = new SockJSSession("123", service);
        sockJSSession.onClose();
        verify(service).onClose();
    }

    @Test
    public void addMessage() throws Exception {
        final SockJSService service = mock(SockJSService.class);
        final SockJSSession sockJSSession = new SockJSSession("123", service);
        sockJSSession.addMessage("hello");
        assertThat(sockJSSession.getAllMessages().length, is(1));
    }

    @Test
    public void addMessages() throws Exception {
        final SockJSService service = mock(SockJSService.class);
        final SockJSSession sockJSSession = new SockJSSession("123", service);
        sockJSSession.addMessages(new String[]{"hello", "world"});
        assertThat(sockJSSession.getAllMessages().length, is(2));
    }

    @Test
    public void clearMessage() throws Exception {
        final SockJSService service = mock(SockJSService.class);
        final SockJSSession sockJSSession = new SockJSSession("123", service);
        sockJSSession.addMessage("hello");
        sockJSSession.clearMessagees();
        assertThat(sockJSSession.getAllMessages().length, is(0));
    }

}
