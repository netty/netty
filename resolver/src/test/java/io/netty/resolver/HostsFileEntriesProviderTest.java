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
package io.netty.resolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HostsFileEntriesProviderTest {

    @Test
    void testParse() throws IOException {
        String hostsString = new StringBuilder()
                .append("127.0.0.1 host1").append("\n") // single hostname, separated with blanks
                .append("::1 host1").append("\n") // same as above, but IPv6
                .append("\n") // empty line
                .append("192.168.0.1\thost2").append("\n") // single hostname, separated with tabs
                .append("#comment").append("\n") // comment at the beginning of the line
                .append(" #comment  ").append("\n") // comment in the middle of the line
                .append("192.168.0.2  host3  #comment").append("\n") // comment after hostname
                .append("192.168.0.3  host4  host5 host6").append("\n") // multiple aliases
                .append("192.168.0.4  host4").append("\n") // host mapped to a second address, must be considered
                .append("192.168.0.5  HOST7").append("\n") // uppercase host, should match lowercase host
                .append("192.168.0.6  host7").append("\n") // must be considered
                .toString();

        HostsFileEntriesProvider entries = HostsFileEntriesProvider.parser()
                .parse(new BufferedReader(new StringReader(hostsString)));
        Map<String, List<InetAddress>> inet4Entries = entries.ipv4Entries();
        Map<String, List<InetAddress>> inet6Entries = entries.ipv6Entries();

        assertEquals(7, inet4Entries.size(), "Expected 7 IPv4 entries");
        assertEquals(1, inet6Entries.size(), "Expected 1 IPv6 entries");

        assertEquals(1, inet4Entries.get("host1").size());
        assertEquals("127.0.0.1", inet4Entries.get("host1").get(0).getHostAddress());

        assertEquals(1, inet4Entries.get("host2").size());
        assertEquals("192.168.0.1", inet4Entries.get("host2").get(0).getHostAddress());

        assertEquals(1, inet4Entries.get("host3").size());
        assertEquals("192.168.0.2", inet4Entries.get("host3").get(0).getHostAddress());

        assertEquals(2, inet4Entries.get("host4").size());
        assertEquals("192.168.0.3", inet4Entries.get("host4").get(0).getHostAddress());
        assertEquals("192.168.0.4", inet4Entries.get("host4").get(1).getHostAddress());

        assertEquals(1, inet4Entries.get("host5").size());
        assertEquals("192.168.0.3", inet4Entries.get("host5").get(0).getHostAddress());

        assertEquals(1, inet4Entries.get("host6").size());
        assertEquals("192.168.0.3", inet4Entries.get("host6").get(0).getHostAddress());

        assertNotNull(inet4Entries.get("host7"), "Uppercase host doesn't resolve");
        assertEquals(2, inet4Entries.get("host7").size());
        assertEquals("192.168.0.5", inet4Entries.get("host7").get(0).getHostAddress());
        assertEquals("192.168.0.6", inet4Entries.get("host7").get(1).getHostAddress());

        assertEquals(1, inet6Entries.get("host1").size());
        assertEquals("0:0:0:0:0:0:0:1", inet6Entries.get("host1").get(0).getHostAddress());
    }

    @Test
    void testCharsetInputValidation() {
        assertThrows(NullPointerException.class, new Executable() {
            @Override
            public void execute() throws IOException {
                HostsFileEntriesProvider.parser().parse((Charset[]) null);
            }
        });

        assertThrows(NullPointerException.class, new Executable() {
            @Override
            public void execute() throws IOException {
                HostsFileEntriesProvider.parser().parse(new File(""), (Charset[]) null);
            }
        });

        assertThrows(NullPointerException.class, new Executable() {
            @Override
            public void execute() {
                HostsFileEntriesProvider.parser().parseSilently((Charset[]) null);
            }
        });

        assertThrows(NullPointerException.class, new Executable() {
            @Override
            public void execute() {
                HostsFileEntriesProvider.parser().parseSilently(new File(""), (Charset[]) null);
            }
        });
    }

    @Test
    void testFileInputValidation() {
        assertThrows(NullPointerException.class, new Executable() {
            @Override
            public void execute() throws IOException {
                HostsFileEntriesProvider.parser().parse((File) null);
            }
        });

        assertThrows(NullPointerException.class, new Executable() {
            @Override
            public void execute() {
                HostsFileEntriesProvider.parser().parseSilently((File) null);
            }
        });
    }

    @Test
    void testReaderInputValidation() {
        assertThrows(NullPointerException.class, new Executable() {
            @Override
            public void execute() throws IOException {
                HostsFileEntriesProvider.parser().parse((Reader) null);
            }
        });
    }
}
