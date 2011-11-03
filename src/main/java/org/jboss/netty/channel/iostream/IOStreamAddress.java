/*
 * Copyright 2011 Red Hat, Inc.
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
package org.jboss.netty.channel.iostream;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketAddress;

/**
 * A {@link java.net.SocketAddress} implementation holding an
 * {@link java.io.InputStream} and an {@link java.io.OutputStream} instance used
 * as "remote" address to connect to with a {@link IOStreamChannel}.
 * 
 * @author Daniel Bimschas
 * @author Dennis Pfisterer
 */
public class IOStreamAddress extends SocketAddress {

    private final InputStream inputStream;

    private final OutputStream outputStream;

    public IOStreamAddress(final InputStream inputStream, final OutputStream outputStream) {

        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }
}
