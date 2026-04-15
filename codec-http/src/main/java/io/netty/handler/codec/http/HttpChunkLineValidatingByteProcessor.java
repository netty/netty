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

    static final class Match extends BitSet {
        private static final long serialVersionUID = 49522994383099834L;
        private final int then;

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

    private enum State {
        Size(
                new Match(SIZE).chars("0123456789abcdefABCDEF \t"),
                new Match(CHUNK_EXT_NAME).chars(";")),
        ChunkExtName(
                new Match(CHUNK_EXT_NAME)
                        .range(0x21, 0x7E)
                        .chars(" \t")
                        .chars("(),/:<=>?@[\\]{}", false),
                new Match(CHUNK_EXT_VAL_START).chars("=")),
        ChunkExtValStart(
                new Match(CHUNK_EXT_VAL_START).chars(" \t"),
                new Match(CHUNK_EXT_VAL_QUOTED).chars("\""),
                new Match(CHUNK_EXT_VAL_TOKEN)
                        .range(0x21, 0x7E)
                        .chars("(),/:<=>?@[\\]{}\"", false)),
        ChunkExtValQuoted(
                new Match(CHUNK_EXT_VAL_QUOTED_ESCAPE).chars("\\"),
                new Match(CHUNK_EXT_VAL_QUOTED_END).chars("\""),
                new Match(CHUNK_EXT_VAL_QUOTED)
                        .chars("\t !")
                        .range(0x23, 0x5B)
                        .range(0x5D, 0x7E)
                        .range(0x80, 0xFF)),
        ChunkExtValQuotedEscape(
                new Match(CHUNK_EXT_VAL_QUOTED)
                        .chars("\t ")
                        .range(0x21, 0x7E)
                        .range(0x80, 0xFF)),
        ChunkExtValQuotedEnd(
                new Match(CHUNK_EXT_VAL_QUOTED_END).chars("\t "),
                new Match(CHUNK_EXT_NAME).chars(";")),
        ChunkExtValToken(
                new Match(CHUNK_EXT_VAL_TOKEN)
                        .range(0x21, 0x7E, true)
                        .chars("(),/:<=>?@[\\]{};", false),
                new Match(CHUNK_EXT_NAME).chars(";")),
        ;

        private final Match[] matches;

        State(Match... matches) {
            this.matches = matches;
        }

        State match(byte value) {
            for (Match match : matches) {
                if (match.get(value)) {
                    return STATES_BY_ORDINAL[match.then];
                }
            }
            if (this == Size) {
                throw new NumberFormatException("Invalid chunk size");
            } else {
                throw new InvalidChunkExtensionException("Invalid chunk extension");
            }
        }
    }

    private static final State[] STATES_BY_ORDINAL = State.values();

    private State state = State.Size;

    @Override
    public boolean process(byte value) {
        state = state.match(value);
        return true;
    }

    public void finish() {
        switch (state) {
            case ChunkExtValQuoted:
            case ChunkExtValQuotedEscape:
            case ChunkExtValStart:
                throw new InvalidChunkExtensionException("Invalid chunk extension");
        }
        // Exhaustiveness check
        assert state == State.Size ||
                state == State.ChunkExtName ||
                state == State.ChunkExtValQuotedEnd ||
                state == State.ChunkExtValToken;
    }
}
