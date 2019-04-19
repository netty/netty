/*
 * Copyright 2019 The Netty Project
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

package io.netty.handler.codec.dns.record.opt;


/**
 * Extension Mechanisms for DNS (EDNS0) <a href="https://tools.ietf.org/html/draft-sekar-dns-llq-01">long lived
 * queries</a> option
 */
public class Edns0LlqOption implements Edns0Option {
    private final short version;
    private final short opcode;
    private final short errCode;
    private final long id;
    private final int leaseLife;

    public Edns0LlqOption(short version, short opcode, short errCode, long id, int leaseLife) {
        this.version = version;
        this.opcode = opcode;
        this.errCode = errCode;
        this.id = id;
        this.leaseLife = leaseLife;
    }

    @Override
    public short optionCode() {
        return Edns0Option.OPTION_CODE_EDNS0_LLQ;
    }

    public short version() {
        return version;
    }

    public short opcode() {
        return opcode;
    }

    public short errCode() {
        return errCode;
    }

    public long id() {
        return id;
    }

    public int leaseLife() {
        return leaseLife;
    }
}
