/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.util.internal;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public final class SocketUtil {
    
    
    private static final String ZONE_ID_SEPARATOR = "%";


    /**
     * Takes care of stripping the zone id / scope Id from the {@link InetAddress} if its an {@link Inet6Address} and the java
     * version is < 7. After that a new {@link InetSocketAddress} is created based on the old one.  This is needed because of a bug that exists in java
     * versions < 7.
     * 
     * See https://github.com/netty/netty/issues/267
     * 
     */
    public static InetSocketAddress stripZoneId(InetSocketAddress socketAddress) {
        // If we have a java version which is >= 7 we can just return the given
        // InetSocketAddress as this bug only seems
        // to exist in java 6 (and maybe also versions before)
        if (DetectionUtil.javaVersion() >= 7) {
            return socketAddress;
        }
        InetAddress address = socketAddress.getAddress();
        if (address instanceof Inet6Address) {
            Inet6Address inet6Address = (Inet6Address) address;

            // Check if its a LinkLocalAddress as this is the only one which is
            // affected
            if (inet6Address.isLinkLocalAddress()) {
                String hostaddress = inet6Address.getHostAddress();

                int separator = hostaddress.indexOf(ZONE_ID_SEPARATOR);
                
                // check if the address contains a zoneId /scopeId and if so strip it
                if (separator != -1) {
                    // strip of the zoneId / scopeId
                     String withoutZonedId = inet6Address.getHostAddress().substring(0, separator);
                     return new InetSocketAddress(withoutZonedId, socketAddress.getPort());
                }
            }
        }
        return socketAddress;
    }
    
    private SocketUtil() {
        
    }
}
