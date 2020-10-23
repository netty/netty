/*
 * Copyright 2016 The Netty Project
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

import io.netty.util.internal.SuppressJava6Requirement;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@SuppressJava6Requirement(reason = "Usage guarded by java version check")
final class Java8SslUtils {

    private Java8SslUtils() { }

    static List<String> getSniHostNames(SSLParameters sslParameters) {
        List<SNIServerName> names = sslParameters.getServerNames();
        if (names == null || names.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> strings = new ArrayList<String>(names.size());

        for (SNIServerName serverName : names) {
            if (serverName instanceof SNIHostName) {
                strings.add(((SNIHostName) serverName).getAsciiName());
            } else {
                throw new IllegalArgumentException("Only " + SNIHostName.class.getName()
                        + " instances are supported, but found: " + serverName);
            }
        }
        return strings;
    }

    static void setSniHostNames(SSLParameters sslParameters, List<String> names) {
        sslParameters.setServerNames(getSniHostNames(names));
    }

    static List getSniHostNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return Collections.emptyList();
        }
        List<SNIServerName> sniServerNames = new ArrayList<SNIServerName>(names.size());
        for (String name: names) {
            sniServerNames.add(new SNIHostName(name));
        }
        return sniServerNames;
    }

    static List getSniHostName(byte[] hostname) {
        if (hostname == null || hostname.length == 0) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new SNIHostName(hostname));
    }

    static boolean getUseCipherSuitesOrder(SSLParameters sslParameters) {
        return sslParameters.getUseCipherSuitesOrder();
    }

    static void setUseCipherSuitesOrder(SSLParameters sslParameters, boolean useOrder) {
        sslParameters.setUseCipherSuitesOrder(useOrder);
    }

    @SuppressWarnings("unchecked")
    static void setSNIMatchers(SSLParameters sslParameters, Collection<?> matchers) {
        sslParameters.setSNIMatchers((Collection<SNIMatcher>) matchers);
    }

    @SuppressWarnings("unchecked")
    static boolean checkSniHostnameMatch(Collection<?> matchers, byte[] hostname) {
        if (matchers != null && !matchers.isEmpty()) {
            SNIHostName name = new SNIHostName(hostname);
            Iterator<SNIMatcher> matcherIt = (Iterator<SNIMatcher>) matchers.iterator();
            while (matcherIt.hasNext()) {
                SNIMatcher matcher = matcherIt.next();
                // type 0 is for hostname
                if (matcher.getType() == 0 && matcher.matches(name)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }
}
