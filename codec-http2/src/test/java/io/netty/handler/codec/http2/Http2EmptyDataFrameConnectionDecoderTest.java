/*
 * Copyright 2019 The Netty Project
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
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Http2EmptyDataFrameConnectionDecoderTest {

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
        Http2EmptyDataFrameConnectionDecoder decoder = new Http2EmptyDataFrameConnectionDecoder(delegate, 2);
        decoder.frameListener(listener);
        verify(delegate).frameListener(listenerArgumentCaptor.capture());

        assertThat(decoder.frameListener(),
                CoreMatchers.not(CoreMatchers.instanceOf(Http2EmptyDataFrameListener.class)));
        assertThat(decoder.frameListener0(), CoreMatchers.instanceOf(Http2EmptyDataFrameListener.class));
    }

    @Test
    public void testDecorationWithNull() {
        Http2ConnectionDecoder delegate = mock(Http2ConnectionDecoder.class);

        Http2EmptyDataFrameConnectionDecoder decoder = new Http2EmptyDataFrameConnectionDecoder(delegate, 2);
        decoder.frameListener(null);
        assertNull(decoder.frameListener());
    }
}
