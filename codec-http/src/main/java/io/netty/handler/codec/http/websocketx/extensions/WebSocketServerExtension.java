/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.http.websocketx.extensions;


/**
 * Created once the handshake phase is done.
 */
public interface WebSocketServerExtension extends WebSocketExtension {

    /**
     * Return an extension configuration to submit to the client as an acknowledge.
     *
     * @return the acknowledged extension configuration.
     */
    //TODO: after migrating to JDK 8 rename this to 'newResponseData()' and mark old as deprecated with default method
    WebSocketExtensionData newReponseData();

}
