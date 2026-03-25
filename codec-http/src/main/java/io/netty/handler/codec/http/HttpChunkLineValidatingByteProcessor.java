/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.util.ByteProcessor;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Validates the chunk start line. That is, the chunk size and chunk extensions, until the CR LF pair.
 * See <a href="https://www.rfc-editor.org/rfc/rfc9112#name-chunked-transfer-coding">RFC 9112 section 7.1</a>.
 *
 * <pre>{@code
 *   chunked-body   = *chunk
 *                    last-chunk
 *                    trailer-section
 *                    CRLF
 *
 *   chunk          = chunk-size [ chunk-ext ] CRLF
 *                    chunk-data CRLF
 *   chunk-size     = 1*HEXDIG
 *   last-chunk     = 1*("0") [ chunk-ext ] CRLF
 *
 *   chunk-data     = 1*OCTET ; a sequence of chunk-size octets
 *   chunk-ext      = *( BWS ";" BWS chunk-ext-name
 *                       [ BWS "=" BWS chunk-ext-val ] )
 *
 *   chunk-ext-name = token
 *   chunk-ext-val  = token / quoted-string
 *   quoted-string  = DQUOTE *( qdtext / quoted-pair ) DQUOTE
 *   qdtext         = HTAB / SP / %x21 / %x23-5B / %x5D-7E / obs-text
 *   quoted-pair    = "\" ( HTAB / SP / VCHAR / obs-text )
 *   obs-text       = %x80-FF
 *   OWS            = *( SP / HTAB )
 *                  ; optional whitespace
 *   BWS            = OWS
 *                  ; "bad" whitespace
 *   VCHAR          =  %x21-7E
 *                  ; visible (printing) characters
 * }</pre>
 */
final class HttpChunkLineValidatingByteProcessor implements ByteProcessor {
    private static final int SIZE = 0;
    private static final int CHUNK_EXT_NAME = 1;
    private static final int CHUNK_EXT_VAL_START = 2;
    private static final int CHUNK_EXT_VAL_QUOTED = 3;
    private static final int CHUNK_EXT_VAL_QUOTED_ESCAPE = 4;
    private static final int CHUNK_EXT_VAL_QUOTED_END = 5;
    private static final int CHUNK_EXT_VAL_TOKEN = 6;
    private static final int N_STATES = 7;

    private static final byte[] TRANSITIONS = buildTable();

    private static byte[] buildTable() {
        byte[] table = new byte[N_STATES * 256];
        Arrays.fill(table, (byte) -1);

        // SIZE: hex digits and whitespace stay in SIZE, ';' transitions to CHUNK_EXT_NAME
        for (int i = 0; i < "0123456789abcdefABCDEF \t".length(); i++) {
            table[SIZE << 8 | "0123456789abcdefABCDEF \t".charAt(i)] = SIZE;
        }
        table[SIZE << 8 | ';'] = CHUNK_EXT_NAME;

        // CHUNK_EXT_NAME: token chars + OWS stay, '=' transitions to VAL_START
        for (int i = 0x21; i <= 0x7E; i++) {
            table[CHUNK_EXT_NAME << 8 | i] = CHUNK_EXT_NAME;
        }
        table[CHUNK_EXT_NAME << 8 | ' '] = CHUNK_EXT_NAME;
        table[CHUNK_EXT_NAME << 8 | '\t'] = CHUNK_EXT_NAME;
        // Exclude non-token delimiters from name
        for (int i = 0; i < "(),/:<=>?@[\\]{}".length(); i++) {
            table[CHUNK_EXT_NAME << 8 | "(),/:<=>?@[\\]{}".charAt(i)] = -1;
        }
        table[CHUNK_EXT_NAME << 8 | '='] = CHUNK_EXT_VAL_START;

        // CHUNK_EXT_VAL_START: OWS stays, '"' goes to QUOTED, token chars go to TOKEN
        table[CHUNK_EXT_VAL_START << 8 | ' '] = CHUNK_EXT_VAL_START;
        table[CHUNK_EXT_VAL_START << 8 | '\t'] = CHUNK_EXT_VAL_START;
        table[CHUNK_EXT_VAL_START << 8 | '"'] = CHUNK_EXT_VAL_QUOTED;
        for (int i = 0x21; i <= 0x7E; i++) {
            if (table[CHUNK_EXT_VAL_START << 8 | i] == -1) {
                table[CHUNK_EXT_VAL_START << 8 | i] = CHUNK_EXT_VAL_TOKEN;
            }
        }
        // Exclude non-token delimiters (including '"' already set above)
        for (int i = 0; i < "(),/:<=>?@[\\]{}".length(); i++) {
            table[CHUNK_EXT_VAL_START << 8 | "(),/:<=>?@[\\]{}".charAt(i)] = -1;
        }

        // CHUNK_EXT_VAL_QUOTED: '\' escapes, '"' ends, qdtext stays
        table[CHUNK_EXT_VAL_QUOTED << 8 | '\\'] = CHUNK_EXT_VAL_QUOTED_ESCAPE;
        table[CHUNK_EXT_VAL_QUOTED << 8 | '"'] = CHUNK_EXT_VAL_QUOTED_END;
        table[CHUNK_EXT_VAL_QUOTED << 8 | '\t'] = CHUNK_EXT_VAL_QUOTED;
        table[CHUNK_EXT_VAL_QUOTED << 8 | ' '] = CHUNK_EXT_VAL_QUOTED;
        table[CHUNK_EXT_VAL_QUOTED << 8 | '!'] = CHUNK_EXT_VAL_QUOTED;
        for (int i = 0x23; i <= 0x5B; i++) {
            table[CHUNK_EXT_VAL_QUOTED << 8 | i] = CHUNK_EXT_VAL_QUOTED;
        }
        for (int i = 0x5D; i <= 0x7E; i++) {
            table[CHUNK_EXT_VAL_QUOTED << 8 | i] = CHUNK_EXT_VAL_QUOTED;
        }
        for (int i = 0x80; i <= 0xFF; i++) {
            table[CHUNK_EXT_VAL_QUOTED << 8 | i] = CHUNK_EXT_VAL_QUOTED;
        }

        // CHUNK_EXT_VAL_QUOTED_ESCAPE: any VCHAR/obs-text/SP/HTAB goes back to QUOTED
        table[CHUNK_EXT_VAL_QUOTED_ESCAPE << 8 | '\t'] = CHUNK_EXT_VAL_QUOTED;
        table[CHUNK_EXT_VAL_QUOTED_ESCAPE << 8 | ' '] = CHUNK_EXT_VAL_QUOTED;
        for (int i = 0x21; i <= 0x7E; i++) {
            table[CHUNK_EXT_VAL_QUOTED_ESCAPE << 8 | i] = CHUNK_EXT_VAL_QUOTED;
        }
        for (int i = 0x80; i <= 0xFF; i++) {
            table[CHUNK_EXT_VAL_QUOTED_ESCAPE << 8 | i] = CHUNK_EXT_VAL_QUOTED;
        }

        // CHUNK_EXT_VAL_QUOTED_END: OWS stays, ';' transitions to CHUNK_EXT_NAME
        table[CHUNK_EXT_VAL_QUOTED_END << 8 | '\t'] = CHUNK_EXT_VAL_QUOTED_END;
        table[CHUNK_EXT_VAL_QUOTED_END << 8 | ' '] = CHUNK_EXT_VAL_QUOTED_END;
        table[CHUNK_EXT_VAL_QUOTED_END << 8 | ';'] = CHUNK_EXT_NAME;

        // CHUNK_EXT_VAL_TOKEN: token chars stay, ';' transitions to CHUNK_EXT_NAME
        for (int i = 0x21; i <= 0x7E; i++) {
            table[CHUNK_EXT_VAL_TOKEN << 8 | i] = CHUNK_EXT_VAL_TOKEN;
        }
        // Exclude non-token delimiters (including ';' which is set below)
        for (int i = 0; i < "(),/:<=>?@[\\]{};".length(); i++) {
            table[CHUNK_EXT_VAL_TOKEN << 8 | "(),/:<=>?@[\\]{};".charAt(i)] = -1;
        }
        table[CHUNK_EXT_VAL_TOKEN << 8 | ';'] = CHUNK_EXT_NAME;

        assert verifyTable(table);
        return table;
    }

    /**
     * Verify the table matches the specification built from Match objects.
     * Only runs when assertions are enabled.
     */
    private static boolean verifyTable(byte[] table) {
        byte[] expected = compile();
        for (int i = 0; i < table.length; i++) {
            if (table[i] != expected[i]) {
                int state = i >> 8;
                int byteVal = i & 0xFF;
                throw new AssertionError(
                        "Transition mismatch at state " + state + " byte 0x" +
                        Integer.toHexString(byteVal) + ": got " + table[i] + " expected " + expected[i]);
            }
        }
        return true;
    }

    static final class Match extends BitSet {
        private static final long serialVersionUID = 49522994383099834L;
        final int then;

        Match(int then) {
            super(256);
            this.then = then;
        }

        Match chars(String chars) {
            return chars(chars, true);
        }

        Match chars(String chars, boolean value) {
            for (int i = 0, len = chars.length(); i < len; i++) {
                set(chars.charAt(i), value);
            }
            return this;
        }

        Match range(int from, int to) {
            return range(from, to, true);
        }

        Match range(int from, int to, boolean value) {
            for (int i = from; i <= to; i++) {
                set(i, value);
            }
            return this;
        }
    }

    /**
     * Build the transition table from Match objects with explicit disjointness.
     * Used only for assertion verification.
     */
    static byte[] compile() {
        Match[][] stateMatchers = new Match[N_STATES][];

        stateMatchers[SIZE] = new Match[]{
                new Match(SIZE).chars("0123456789abcdefABCDEF \t"),
                new Match(CHUNK_EXT_NAME).chars(";")
        };
        stateMatchers[CHUNK_EXT_NAME] = new Match[]{
                new Match(CHUNK_EXT_NAME)
                        .range(0x21, 0x7E)
                        .chars(" \t")
                        .chars("(),/:<=>?@[\\]{}", false),
                new Match(CHUNK_EXT_VAL_START).chars("=")
        };
        stateMatchers[CHUNK_EXT_VAL_START] = new Match[]{
                new Match(CHUNK_EXT_VAL_START).chars(" \t"),
                new Match(CHUNK_EXT_VAL_QUOTED).chars("\""),
                // explicitly exclude '"' (0x22) — disjoint with the matcher above
                new Match(CHUNK_EXT_VAL_TOKEN)
                        .range(0x21, 0x7E)
                        .chars("(),/:<=>?@[\\]{}\"", false)
        };
        stateMatchers[CHUNK_EXT_VAL_QUOTED] = new Match[]{
                new Match(CHUNK_EXT_VAL_QUOTED_ESCAPE).chars("\\"),
                new Match(CHUNK_EXT_VAL_QUOTED_END).chars("\""),
                // 0x23-0x5B excludes '"' (0x22), 0x5D-0x7E excludes '\' (0x5C) — disjoint by range
                new Match(CHUNK_EXT_VAL_QUOTED)
                        .chars("\t !")
                        .range(0x23, 0x5B)
                        .range(0x5D, 0x7E)
                        .range(0x80, 0xFF)
        };
        stateMatchers[CHUNK_EXT_VAL_QUOTED_ESCAPE] = new Match[]{
                new Match(CHUNK_EXT_VAL_QUOTED)
                        .chars("\t ")
                        .range(0x21, 0x7E)
                        .range(0x80, 0xFF)
        };
        stateMatchers[CHUNK_EXT_VAL_QUOTED_END] = new Match[]{
                new Match(CHUNK_EXT_VAL_QUOTED_END).chars("\t "),
                new Match(CHUNK_EXT_NAME).chars(";")
        };
        stateMatchers[CHUNK_EXT_VAL_TOKEN] = new Match[]{
                // explicitly exclude ';' (0x3B) — disjoint with the matcher below
                new Match(CHUNK_EXT_VAL_TOKEN)
                        .range(0x21, 0x7E)
                        .chars("(),/:<=>?@[\\]{};", false),
                new Match(CHUNK_EXT_NAME).chars(";")
        };

        byte[] table = new byte[N_STATES * 256];
        Arrays.fill(table, (byte) -1);
        for (int state = 0; state < N_STATES; state++) {
            int base = state << 8;
            for (Match m : stateMatchers[state]) {
                for (int i = m.nextSetBit(0); i >= 0; i = m.nextSetBit(i + 1)) {
                    if (table[base | i] != -1) {
                        throw new IllegalStateException(
                                "overlapping matchers at byte 0x" + Integer.toHexString(i) +
                                " in state " + state);
                    }
                    table[base | i] = (byte) m.then;
                }
            }
        }
        return table;
    }

    private int state = SIZE;

    @Override
    public boolean process(byte value) {
        int next = TRANSITIONS[state << 8 | (value & 0xFF)];
        if (next < 0) {
            if (state == SIZE) {
                throw new NumberFormatException("Invalid chunk size");
            }
            throw new InvalidChunkExtensionException("Invalid chunk extension");
        }
        state = next;
        return true;
    }

    public void finish() {
        if (state != SIZE && state != CHUNK_EXT_NAME
                && state != CHUNK_EXT_VAL_TOKEN && state != CHUNK_EXT_VAL_QUOTED_END) {
            throw new InvalidChunkExtensionException("Invalid chunk extension");
        }
    }
}
