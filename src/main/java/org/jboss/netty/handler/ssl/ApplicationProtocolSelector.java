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
package org.jboss.netty.handler.ssl;

import java.util.List;

/**
 * Selects an application layer protocol in SSL NPN or ALPM.
 */
public interface ApplicationProtocolSelector {
    /**
     * Invoked to select a protocol from the list of specified application layer protocols.
     *
     * @param protocols the list of application layer protocols sent by the server.
     *                  The list is empty if the server supports neither NPN nor ALPM.
     * @return the selected protocol. {@code null} if no protocol was selected.
     */
    String selectProtocol(List<String> protocols) throws Exception;
}
