/*
 * Copyright 2012 The Netty Project
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

package org.jboss.netty.handler.codec.oneone;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;

/**
 * Special {@link OneToOneEncoder} which enforce strict ordering of encoding and writing. This
 * class should get extend by implementations that needs this enforcement to guaranteer no corruption.
 * Basically all "message" based {@link OneToOneEncoder} mostly don't need this, where "stream" based
 * are often in need of it.
 *
 */
public abstract class OneToOneStrictEncoder extends OneToOneEncoder {

    @Override
    protected boolean doEncode(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        // Synchronize on the channel to guaranteer the strict ordering
        synchronized (ctx.getChannel()) {
            return super.doEncode(ctx, e);
        }
    }
}
