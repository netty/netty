/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.codec.smtp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.AsciiString;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.UnstableApi;

import java.util.HashMap;
import java.util.Map;

/**
 * The command part of a {@link SmtpRequest}.
 */
@UnstableApi
public final class SmtpCommand {
    public static final SmtpCommand EHLO = new SmtpCommand(AsciiString.cached("EHLO"));
    public static final SmtpCommand HELO = new SmtpCommand(AsciiString.cached("HELO"));
    public static final SmtpCommand AUTH = new SmtpCommand(AsciiString.cached("AUTH"));
    public static final SmtpCommand MAIL = new SmtpCommand(AsciiString.cached("MAIL"));
    public static final SmtpCommand RCPT = new SmtpCommand(AsciiString.cached("RCPT"));
    public static final SmtpCommand DATA = new SmtpCommand(AsciiString.cached("DATA"));
    public static final SmtpCommand NOOP = new SmtpCommand(AsciiString.cached("NOOP"));
    public static final SmtpCommand RSET = new SmtpCommand(AsciiString.cached("RSET"));
    public static final SmtpCommand EXPN = new SmtpCommand(AsciiString.cached("EXPN"));
    public static final SmtpCommand VRFY = new SmtpCommand(AsciiString.cached("VRFY"));
    public static final SmtpCommand HELP = new SmtpCommand(AsciiString.cached("HELP"));
    public static final SmtpCommand QUIT = new SmtpCommand(AsciiString.cached("QUIT"));
    public static final SmtpCommand EMPTY = new SmtpCommand(AsciiString.cached(""));

    private static final Map<String, SmtpCommand> COMMANDS = new HashMap<String, SmtpCommand>();
    static {
        COMMANDS.put(EHLO.name().toString(), EHLO);
        COMMANDS.put(HELO.name().toString(), HELO);
        COMMANDS.put(AUTH.name().toString(), AUTH);
        COMMANDS.put(MAIL.name().toString(), MAIL);
        COMMANDS.put(RCPT.name().toString(), RCPT);
        COMMANDS.put(DATA.name().toString(), DATA);
        COMMANDS.put(NOOP.name().toString(), NOOP);
        COMMANDS.put(RSET.name().toString(), RSET);
        COMMANDS.put(EXPN.name().toString(), EXPN);
        COMMANDS.put(VRFY.name().toString(), VRFY);
        COMMANDS.put(HELP.name().toString(), HELP);
        COMMANDS.put(QUIT.name().toString(), QUIT);
        COMMANDS.put(EMPTY.name().toString(), EMPTY);
    }

    /**
     * Returns the {@link SmtpCommand} for the given command name.
     */
    public static SmtpCommand valueOf(CharSequence commandName) {
        ObjectUtil.checkNotNull(commandName, "commandName");
        SmtpCommand command = COMMANDS.get(commandName.toString());
        return command != null ? command : new SmtpCommand(AsciiString.of(commandName));
    }

    private final AsciiString name;

    private SmtpCommand(AsciiString name) {
        this.name = name;
    }

    /**
     * Return the command name.
     */
    public AsciiString name() {
        return name;
    }

    void encode(ByteBuf buffer) {
        ByteBufUtil.writeAscii(buffer, name);
    }

    boolean isContentExpected() {
        return this.equals(DATA);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof SmtpCommand)) {
            return false;
        }
        return name.contentEqualsIgnoreCase(((SmtpCommand) obj).name());
    }

    @Override
    public String toString() {
        return "SmtpCommand{name=" + name + '}';
    }
}
