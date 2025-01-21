/*
 * Copyright 2023 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public abstract class AbstractDecoratingHttp2ConnectionDecoderTest {

    protected abstract DecoratingHttp2ConnectionDecoder newDecoder(Http2ConnectionDecoder decoder);

    protected abstract Class<? extends Http2FrameListener> delegatingFrameListenerType();

    @Test
    public void testDecoration() {
        Http2ConnectionDecoder delegate = mock(Http2ConnectionDecoder.class);
        final ArgumentCaptor<Http2FrameListener> listenerArgumentCaptor =
                ArgumentCaptor.forClass(Http2FrameListener.class);
        when(delegate.frameListener()).then(new Answer<Http2FrameListener>() {
            @Override
            public Http2FrameListener answer(InvocationOnMock invocationOnMock) {
                return listenerArgumentCaptor.getValue();
            }
        });
        Http2FrameListener listener = mock(Http2FrameListener.class);
        DecoratingHttp2ConnectionDecoder decoder = newDecoder(delegate);
        decoder.frameListener(listener);
        verify(delegate).frameListener(listenerArgumentCaptor.capture());

        assertThat(decoder.frameListener(),
                CoreMatchers.not(CoreMatchers.instanceOf(delegatingFrameListenerType())));
    }

    @Test
    public void testDecorationWithNull() {
        Http2ConnectionDecoder delegate = mock(Http2ConnectionDecoder.class);

        DecoratingHttp2ConnectionDecoder decoder = newDecoder(delegate);
        decoder.frameListener(null);
        assertNull(decoder.frameListener());
    }
}
