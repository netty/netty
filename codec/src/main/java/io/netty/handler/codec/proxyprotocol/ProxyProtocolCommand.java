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
package io.netty.handler.codec.proxyprotocol;

import java.util.HashMap;
import java.util.Map;

/**
 * The command of a proxy protocol header
 */
public final class ProxyProtocolCommand implements Comparable<ProxyProtocolCommand> {
    /**
     * The LOCAL command represents a connection that was established on purpose by the proxy
     * without being relayed
     */
    public static final ProxyProtocolCommand LOCAL = new ProxyProtocolCommand("LOCAL", (byte) 0x00);

    /**
     * The PROXY command represents a connection that was established on behalf of another node,
     * and reflects the original connection endpoints
     */
    public static final ProxyProtocolCommand PROXY = new ProxyProtocolCommand("PROXY", (byte) 0x01);

    private static final Map<Byte, ProxyProtocolCommand> commandMap =
            new HashMap<Byte, ProxyProtocolCommand>(2);

    static {
        commandMap.put(LOCAL.byteValue(), LOCAL);
        commandMap.put(PROXY.byteValue(), PROXY);
    }

    private final String name;
    private final byte cmdByte;

    /**
     * Creates a new instance.
     */
    private ProxyProtocolCommand(String name, byte cmdByte) {
        this.name = name;
        this.cmdByte = cmdByte;
    }

    /**
     * Returns the {@link ProxyProtocolCommand} represented by the specified command byte.
     *
     * @param cmdByte  Command byte
     * @return         {@link ProxyProtocolCommand} instance OR <code>null</code> if the command is not recognized
     */
    public static ProxyProtocolCommand valueOf(byte cmdByte) {
        return commandMap.get(cmdByte);
    }

    /**
     * Returns the name of this command.
     *
     * @return The name of this command
     */
    public String name() {
        return name;
    }

    /**
     * Returns the byte value of this command.
     *
     * @return The byte value of this command
     */
    public byte byteValue() {
        return cmdByte;
    }

    @Override
    public int hashCode() {
        return byteValue();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ProxyProtocolCommand)) {
            return false;
        }

        ProxyProtocolCommand that = (ProxyProtocolCommand) o;
        return byteValue() == that.byteValue();
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public int compareTo(ProxyProtocolCommand o) {
        return Byte.valueOf(byteValue()).compareTo(Byte.valueOf(o.byteValue()));
    }

}
