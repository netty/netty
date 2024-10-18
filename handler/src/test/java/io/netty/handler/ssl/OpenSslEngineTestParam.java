/*
 * Copyright 2021 The Netty Project
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

import java.util.List;

final class OpenSslEngineTestParam extends SSLEngineTest.SSLEngineTestParam {
    final boolean useTasks;
    final boolean useTickets;

    static void expandCombinations(SSLEngineTest.SSLEngineTestParam param,
                                   List<? super OpenSslEngineTestParam> output) {
        output.add(new OpenSslEngineTestParam(true, false, param));
        output.add(new OpenSslEngineTestParam(false, false, param));
        if (OpenSsl.isBoringSSL()) {
            output.add(new OpenSslEngineTestParam(true, true, param));
            output.add(new OpenSslEngineTestParam(false, true, param));
        }
    }

    @SuppressWarnings("deprecation")
    static SslContext wrapContext(SSLEngineTest.SSLEngineTestParam param, SslContext context) {
        if (context instanceof OpenSslContext) {
            OpenSslContext ctx = (OpenSslContext) context;
            if (param instanceof OpenSslEngineTestParam) {
                OpenSslEngineTestParam openSslParam = (OpenSslEngineTestParam) param;
                ctx.setUseTasks(openSslParam.useTasks);
                if (openSslParam.useTickets) {
                    ctx.sessionContext().setTicketKeys();
                }
            }
            // Explicit enable the session cache as its disabled by default on the client side.
            ctx.sessionContext().setSessionCacheEnabled(true);
        }
        return context;
    }

    static boolean isUsingTickets(SSLEngineTest.SSLEngineTestParam param) {
        if (param instanceof OpenSslEngineTestParam) {
            return ((OpenSslEngineTestParam) param).useTickets;
        }
        return false;
    }

    OpenSslEngineTestParam(boolean useTasks, boolean useTickets, SSLEngineTest.SSLEngineTestParam param) {
        super(param.type(), param.combo(), param.delegate());
        this.useTasks = useTasks;
        this.useTickets = useTickets;
    }

    @Override
    public String toString() {
        return "OpenSslEngineTestParam{" +
                "type=" + type() +
                ", protocolCipherCombo=" + combo() +
                ", delegate=" + delegate() +
                ", useTasks=" + useTasks +
                ", useTickets=" + useTickets +
                '}';
    }
}
