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
    public static final SmtpCommand EHLO = new SmtpCommand(new AsciiString("EHLO"), false);
    public static final SmtpCommand HELO = new SmtpCommand(new AsciiString("HELO"), false);
    public static final SmtpCommand MAIL = new SmtpCommand(new AsciiString("MAIL"), false);
    public static final SmtpCommand RCPT = new SmtpCommand(new AsciiString("RCPT"), false);
    public static final SmtpCommand DATA = new SmtpCommand(new AsciiString("DATA"), true);
    public static final SmtpCommand NOOP = new SmtpCommand(new AsciiString("NOOP"), false);
    public static final SmtpCommand RSET = new SmtpCommand(new AsciiString("RSET"), false);
    public static final SmtpCommand EXPN = new SmtpCommand(new AsciiString("EXPN"), false);
    public static final SmtpCommand VRFY = new SmtpCommand(new AsciiString("VRFY"), false);
    public static final SmtpCommand HELP = new SmtpCommand(new AsciiString("HELP"), false);
    public static final SmtpCommand QUIT = new SmtpCommand(new AsciiString("QUIT"), false);

    private static final CharSequence DATA_CMD = new AsciiString("DATA");
    private static final Map<CharSequence, SmtpCommand> COMMANDS = new HashMap<CharSequence, SmtpCommand>();
    static {
        COMMANDS.put(EHLO.name(), EHLO);
        COMMANDS.put(HELO.name(), HELO);
        COMMANDS.put(MAIL.name(), MAIL);
        COMMANDS.put(RCPT.name(), RCPT);
        COMMANDS.put(DATA.name(), DATA);
        COMMANDS.put(NOOP.name(), NOOP);
        COMMANDS.put(RSET.name(), RSET);
        COMMANDS.put(EXPN.name(), EXPN);
        COMMANDS.put(VRFY.name(), VRFY);
        COMMANDS.put(HELP.name(), HELP);
        COMMANDS.put(QUIT.name(), QUIT);
    }

    /**
     * Returns the {@link SmtpCommand} for the given command name.
     */
    public static SmtpCommand valueOf(CharSequence commandName) {
        SmtpCommand command = COMMANDS.get(commandName);
        if (command != null) {
            return command;
        }
        return new SmtpCommand(AsciiString.of(ObjectUtil.checkNotNull(commandName, "commandName")),
                               AsciiString.contentEqualsIgnoreCase(commandName, DATA_CMD));
    }

    private final AsciiString name;
    private final boolean contentExpected;
    private int hashCode;

    private SmtpCommand(AsciiString name, boolean contentExpected) {
        this.name = name;
        this.contentExpected = contentExpected;
    }

    /**
     * Return the command name.
     */
    public AsciiString name() {
        return name;
    }

    void encode(ByteBuf buffer) {
        ByteBufUtil.writeAscii(buffer, name());
    }

    boolean isContentExpected() {
        return contentExpected;
    }

    @Override
    public int hashCode() {
        if (hashCode != -1) {
            hashCode = AsciiString.hashCode(name);
        }
        return hashCode;
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
        return "SmtpCommand{" +
                "name=" + name +
                ", contentExpected=" + contentExpected +
                ", hashCode=" + hashCode +
                '}';
    }
}
