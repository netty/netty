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
package io.netty.handler.codec.haproxy;

/**
 * The command of an HAProxy proxy protocol header
 */
public final class HAProxyProtocolCommand implements Comparable<HAProxyProtocolCommand> {
    /**
     * The command is specified in the lowest 4 bits of the protocol version and command byte
     */
    private static final byte COMMAND_MASK = (byte) 0x0f;

    /**
     * Version byte constants
     */
    private static final byte LOCAL_BYTE = (byte) 0x00;
    private static final byte PROXY_BYTE = (byte) 0x01;

    /**
     * The LOCAL command represents a connection that was established on purpose by the proxy
     * without being relayed
     */
    public static final HAProxyProtocolCommand LOCAL = new HAProxyProtocolCommand("LOCAL", LOCAL_BYTE);

    /**
     * The PROXY command represents a connection that was established on behalf of another node,
     * and reflects the original connection endpoints
     */
    public static final HAProxyProtocolCommand PROXY = new HAProxyProtocolCommand("PROXY", PROXY_BYTE);

    private final String name;
    private final byte cmdByte;

    /**
     * Creates a new instance
     */
    private HAProxyProtocolCommand(String name, byte cmdByte) {
        this.name = name;
        this.cmdByte = cmdByte;
    }

    /**
     * Returns the {@link HAProxyProtocolCommand} represented by the specified protocol version and command byte
     *
     * @param verCmdByte protocol version and command byte
     * @return           {@link HAProxyProtocolCommand} instance OR {@code null} if the command is not recognized
     */
    public static HAProxyProtocolCommand valueOf(byte verCmdByte) {
        switch ((byte) (verCmdByte & COMMAND_MASK)) {
            case PROXY_BYTE:
                return PROXY;
            case LOCAL_BYTE:
                return LOCAL;
            default:
                return null;
        }
    }

    /**
     * Returns the name of this command
     *
     * @return the name of this command
     */
    public String name() {
        return name;
    }

    /**
     * Returns the byte value of this command
     *
     * @return the byte value of this command
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
        if (!(o instanceof HAProxyProtocolCommand)) {
            return false;
        }

        HAProxyProtocolCommand that = (HAProxyProtocolCommand) o;
        return byteValue() == that.byteValue();
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public int compareTo(HAProxyProtocolCommand o) {
        return byteValue() - o.byteValue();
    }
}
