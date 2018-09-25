/*
 * Copyright 2017 The Netty Project
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
package io.netty.handler.codec.dns;

import java.util.Locale;
import java.util.regex.Pattern;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

public class DefaultDnsTxtRecord extends AbstractDnsRecord implements DnsTxtRecord {
    private static final Pattern RE_KEY = Pattern.compile("`([`=\\s])");
    private static final Pattern RE_VALUE = Pattern.compile("``");

    private final String key;
    private final String value;

    public DefaultDnsTxtRecord(String name, int dnsClass, long timeToLive, String text) {
        super(name, DnsRecordType.TXT, dnsClass, timeToLive);

        String[] parts = decodeTxt(checkNotNull(text, "text"));

        this.key = parts[0];
        this.value = parts[1];
    }

    @Override
    public String key() {
        return this.key;
    }

    @Override
    public String value() {
        return this.value;
    }

    static String[] decodeTxt(String s) {
        String key = null, value = null;

        int start = 0;
        for (start = 0; start < s.length(); start++) {
            if (!Character.isWhitespace(s.charAt(start))) {
                break;
            }
        }

        char prev = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c == '=' && prev != '`') {
                key = s.substring(start, i);
                value = s.substring(i + 1);

                break;
            }

            prev = c;
        }

        if (key == null) {
            key = "";
            value = s;
        }

        key = RE_KEY.matcher(key).replaceAll("$1").toLowerCase(Locale.US);
        value = RE_VALUE.matcher(value).replaceAll("`");

        return new String[]{key, value};
    }
}
