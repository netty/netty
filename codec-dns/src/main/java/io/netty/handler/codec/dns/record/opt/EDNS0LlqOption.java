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
 * EDNS0 long live queries option
 * <p>
 * RDATA Format:
 * <pre>
 *   Field Name        Field Type     Description
 *   ---------------------------------------------------------------------
 *   OPTION-CODE       u_int16_t      LLQ
 *   OPTION-LENGTH     u_int16_t      Length of following fields, as
 *                                    appropriate
 *   VERSION           u_int16_t      Version of LLQ protocol implemented
 *   LLQ-OPCODE        u_int16_t      Identifies LLQ operation
 *   ERROR-CODE        u_int16_t      Identifies LLQ errors
 *   LLQ-ID            u_int64_t      Identifier for an LLQ
 *   LEASE-LIFE        u_int32_t      Requested or granted life of LLQ, in
 *                                    seconds
 * </pre>
 * <p>
 * {@link EDNS0LlqOption} define the LLQ option frame.
 *
 * @see <a href="https://tools.ietf.org/html/draft-sekar-dns-llq-01">:w
 * https://tools.ietf.org/html/draft-sekar-dns-llq-01</a>
 */
public class EDNS0LlqOption implements EDNS0Option {
    private final short version;
    private final short opcode;
    private final short errCode;
    private final long id;
    private final int leaseLife;

    public EDNS0LlqOption(short version, short opcode, short errCode, long id, int leaseLife) {
        this.version = version;
        this.opcode = opcode;
        this.errCode = errCode;
        this.id = id;
        this.leaseLife = leaseLife;
    }

    @Override
    public EDNS0OptionCode optionCode() {
        return EDNS0OptionCode.LLQ;
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
