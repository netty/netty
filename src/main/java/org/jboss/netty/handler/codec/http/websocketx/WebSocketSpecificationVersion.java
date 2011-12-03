/*
 * Copyright 2010 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.http.websocketx;

/**
 * <p>
 * Versions of the web socket specification.
 * </p>
 * <p>
 * A specification is tied to one wire protocol version but a protocol version
 * may have use by more than 1 version of the specification.
 * </p>
 * 
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 */
public enum WebSocketSpecificationVersion {
    UNKNOWN,

    /**
     * <a href=
     * "http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-00"
     * >draft-ietf-hybi-thewebsocketprotocol- 00</a>.
     */
    V00,

    /**
     * <a href=
     * "http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-10"
     * >draft-ietf-hybi-thewebsocketprotocol- 10</a>
     */
    V10,
    
    /**
     * <a href=
     * "http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-17"
     * >draft-ietf-hybi-thewebsocketprotocol- 17</a>
     */
    V17
    
}
