/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ssl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

public class SilenceExceptionHandler extends ChannelInboundHandlerAdapter {
    private final List<Predicate<Throwable>> rules;

    public SilenceExceptionHandler() {
        this.rules = new CopyOnWriteArrayList<>();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        for (Predicate<Throwable> rule : rules) {
            if (rule.test(cause)) {
                return;
            }
        }
        ctx.fireExceptionCaught(cause);
    }

    public SilenceExceptionHandler rootCauseInstanceOf(Class<? extends Throwable> type) {
        rules.add(thr -> {
            Throwable root = thr;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            return type.isInstance(root);
        });
        return this;
    }

    public SilenceExceptionHandler messageContains(String message) {
        rules.add(thr -> {
            return thr.getMessage().contains(message);
        });
        return this;
    }
}
