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
package io.netty.handler.codec.sockjs;


public class CloseService implements SockJSService {

    private final Config config;

    public CloseService(final Config config) {
        this.config = config;
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public void onMessage(final String message) throws Exception {
        System.out.println("CloseService :" + message);
    }

    @Override
    public void onOpen(final SessionContext session) {
        System.out.println("CloseService onOpen:" + session);
        session.close();
    }

    @Override
    public void onClose() {
        System.out.println("CloseService onClose()");
    }

}
