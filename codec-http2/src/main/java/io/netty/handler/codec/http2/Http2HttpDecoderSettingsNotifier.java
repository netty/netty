/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import io.netty.channel.ChannelHandlerContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides a subscriber interface to notify listeners when a SETTINGS frame is read
 */
public class Http2HttpDecoderSettingsNotifier extends Http2HttpDecoder {
  private List<Http2EventListener<Http2Settings>> settingsListeners;

  public Http2HttpDecoderSettingsNotifier(int maxContentLength) {
    super(maxContentLength);
    init();
  }

  public Http2HttpDecoderSettingsNotifier(int maxContentLength, boolean validateHttpHeaders) {
    super(maxContentLength, validateHttpHeaders);
    init();
  }

  /**
   * Common class initialization method
   */
  private void init() {
    settingsListeners = new ArrayList<Http2EventListener<Http2Settings>>();
  }

  /**
   * Subscribe to SETTINGS read event notification
   * @param listener The object which will receive notifications
   */
  public void addListener(Http2EventListener<Http2Settings> listener) {
    settingsListeners.add(listener);
    // NOTE: We are not tracking if the settings have already been read
    // or if an exception has been raised. If a listener subscribes after these events
    // then they will not be notified.
  }

  /**
   * Unsubscribe from SETTINGS read event notifications
   * @param listener The object to unsubscribe
   * @return <tt>true</tt> if {@code listener} was subscribed at least 1 time and was removed 1 time
   */
  public boolean removeListener(Http2EventListener<Http2Settings> listener) {
    return settingsListeners.remove(listener);
  }

  @Override
  public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
    super.onSettingsRead(ctx, settings);
    for (Http2EventListener<Http2Settings> listener : settingsListeners) {
      listener.done(settings);
    }
  }
}
