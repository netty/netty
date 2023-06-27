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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class DecoratingHttp2ConnectionEncoderTest {

    @Test
    public void testConsumeReceivedSettingsThrows() {
        Http2ConnectionEncoder encoder = mock(Http2ConnectionEncoder.class);
        final DecoratingHttp2ConnectionEncoder decoratingHttp2ConnectionEncoder =
                new DecoratingHttp2ConnectionEncoder(encoder);
        assertThrows(IllegalStateException.class, new Executable() {
            @Override
            public void execute() {
                decoratingHttp2ConnectionEncoder.consumeReceivedSettings(Http2Settings.defaultSettings());
            }
        });
    }

    @Test
    public void testConsumeReceivedSettingsDelegate() {
        TestHttp2ConnectionEncoder encoder = mock(TestHttp2ConnectionEncoder.class);
        DecoratingHttp2ConnectionEncoder decoratingHttp2ConnectionEncoder =
                new DecoratingHttp2ConnectionEncoder(encoder);

        Http2Settings settings = Http2Settings.defaultSettings();
        decoratingHttp2ConnectionEncoder.consumeReceivedSettings(Http2Settings.defaultSettings());
        verify(encoder, times(1)).consumeReceivedSettings(eq(settings));
    }

    private interface TestHttp2ConnectionEncoder extends Http2ConnectionEncoder, Http2SettingsReceivedConsumer { }
}
