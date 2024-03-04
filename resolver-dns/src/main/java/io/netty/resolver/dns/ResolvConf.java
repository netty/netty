/*
 * Copyright 2018 The Netty Project
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
package io.netty.resolver.dns;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Looks up the {@code nameserver}s from the {@code /etc/resolv.conf} file, intended for Linux and macOS.
 */
public final class ResolvConf {
    private final List<InetSocketAddress> nameservers;

    /**
     * Reads from the given reader and extracts the {@code nameserver}s using the syntax of the
     * {@code /etc/resolv.conf} file, see {@code man resolv.conf}.
     */
    public static ResolvConf fromReader(BufferedReader reader) throws IOException {
        return new ResolvConf(reader);
    }

    /**
     * Reads the given file and extracts the {@code nameserver}s using the syntax of the
     * {@code /etc/resolv.conf} file, see {@code man resolv.conf}.
     */
    public static ResolvConf fromFile(String file) throws IOException {
        FileReader fileReader = new FileReader(file);
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            return fromReader(reader);
        } finally {
            fileReader.close();
        }
    }

    /**
     * Returns the {@code nameserver}s from the {@code /etc/resolv.conf} file. The file is only read once
     * during the lifetime of this class.
     */
    public static ResolvConf system() {
        ResolvConf resolvConv = ResolvConfLazy.machineResolvConf;
        if (resolvConv != null) {
            return resolvConv;
        }
        throw new IllegalStateException("/etc/resolv.conf could not be read");
    }

    private ResolvConf(BufferedReader reader) throws IOException {
        List<InetSocketAddress> nameservers = new ArrayList<InetSocketAddress>();
        String ln;
        while ((ln = reader.readLine()) != null) {
            ln = ln.trim();
            if (ln.isEmpty()) {
                continue;
            }

            if (ln.startsWith("nameserver")) {
                ln = ln.substring("nameserver".length()).trim();
                nameservers.add(new InetSocketAddress(ln, 53));
            }
        }
        this.nameservers = Collections.unmodifiableList(nameservers);
    }

    public List<InetSocketAddress> getNameservers() {
        return nameservers;
    }

    static final class ResolvConfLazy {
        static final ResolvConf machineResolvConf;

        static {
            ResolvConf resolvConf;
            try {
                resolvConf = ResolvConf.fromFile("/etc/resolv.conf");
            } catch (IOException e) {
                resolvConf = null;
            }
            machineResolvConf = resolvConf;
        }
    }
}
