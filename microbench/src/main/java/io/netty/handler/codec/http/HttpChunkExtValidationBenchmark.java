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

import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.ByteProcessor;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.CompilerControl.Mode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.BitSet;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

/**
 * Isolated benchmark for the {@code process(byte)} loop of chunk extension validation.
 * Compares the new flat transition table against the old enum + Match[] (BitSet) approach.
 * <p>
 * Placed in the same package as {@link HttpChunkLineValidatingByteProcessor} to access it directly.
 */
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(2)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
public class HttpChunkExtValidationBenchmark extends AbstractMicrobenchmark {

    private static final int NUM_INPUTS = 64;
    private static final long SEED = 0xDEADBEEFCAFEBABEL;

    // Token characters: VCHAR minus delimiters "(),/:;<=>?@[\]{}
    private static final byte[] TOKEN_CHARS;
    // Hex digits
    private static final byte[] HEX_CHARS = "0123456789abcdefABCDEF".getBytes();
    // qdtext characters (subset for generation): printable ASCII minus '"' and '\'
    private static final byte[] QDTEXT_CHARS;

    static {
        // Build token char array: 0x21-0x7E minus delimiters
        String delimiters = "\"(),/:;<=>?@[\\]{}";
        int count = 0;
        for (int i = 0x21; i <= 0x7E; i++) {
            if (delimiters.indexOf(i) < 0) {
                count++;
            }
        }
        TOKEN_CHARS = new byte[count];
        int idx = 0;
        for (int i = 0x21; i <= 0x7E; i++) {
            if (delimiters.indexOf(i) < 0) {
                TOKEN_CHARS[idx++] = (byte) i;
            }
        }

        // Build qdtext: HTAB, SP, '!', 0x23-0x5B, 0x5D-0x7E
        count = 2 + 1 + (0x5B - 0x23 + 1) + (0x7E - 0x5D + 1); // HTAB, SP, '!', ranges
        QDTEXT_CHARS = new byte[count];
        idx = 0;
        QDTEXT_CHARS[idx++] = '\t';
        QDTEXT_CHARS[idx++] = ' ';
        QDTEXT_CHARS[idx++] = '!';
        for (int i = 0x23; i <= 0x5B; i++) {
            QDTEXT_CHARS[idx++] = (byte) i;
        }
        for (int i = 0x5D; i <= 0x7E; i++) {
            QDTEXT_CHARS[idx++] = (byte) i;
        }
    }

    @Param({ "64", "256", "1024" })
    int length;

    private byte[][] inputs;
    private long next;
    private final HttpChunkLineValidatingByteProcessor flatProcessor = new HttpChunkLineValidatingByteProcessor();
    private final OldProcessor oldProcessor = new OldProcessor();

    @Setup
    public void setup() {
        inputs = new byte[NUM_INPUTS][];
        SplittableRandom rng = new SplittableRandom(SEED);
        for (int i = 0; i < NUM_INPUTS; i++) {
            inputs[i] = generateValidInput(rng, length);
            // Validate with both processors
            HttpChunkLineValidatingByteProcessor p = new HttpChunkLineValidatingByteProcessor();
            OldProcessor op = new OldProcessor();
            for (byte b : inputs[i]) {
                p.process(b);
                op.process(b);
            }
        }
        next = 0;
    }

    /**
     * Generate a valid chunk line byte sequence covering all states.
     * <p>
     * Uses patterns compatible with BOTH old (enum+Match[]) and new (flat table) implementations.
     * The old code has a bug where ';' inside CHUNK_EXT_VAL_TOKEN is not excluded, so we avoid
     * chaining extensions after token values. After a token value, we pad with token chars to fill.
     * Extensions are only chained after quoted values (where ';' correctly starts a new extension).
     * <p>
     * Pattern: hex (";" name ["=" (token-to-end | quoted-val)])* pad-with-token
     */
    private static byte[] generateValidInput(SplittableRandom rng, int length) {
        byte[] buf = new byte[length * 2]; // oversize buffer, will trim
        int pos = 0;

        // Hex prefix: 1-4 hex digits
        int hexLen = 1 + rng.nextInt(4);
        for (int i = 0; i < hexLen; i++) {
            buf[pos++] = HEX_CHARS[rng.nextInt(HEX_CHARS.length)];
        }

        while (pos < length) {
            // Start a new extension
            buf[pos++] = ';';
            if (pos >= length) {
                break;
            }

            // Extension name: 2-8 token chars
            int nameLen = 2 + rng.nextInt(7);
            for (int i = 0; i < nameLen && pos < length; i++) {
                buf[pos++] = TOKEN_CHARS[rng.nextInt(TOKEN_CHARS.length)];
            }

            // Decide value type: 0=no value (name only), 1=quoted, 2=token (terminal — fills rest)
            int valueType = rng.nextInt(3);

            if (valueType == 0 || pos >= length) {
                // Name-only extension, can chain more
                continue;
            }

            buf[pos++] = '=';
            if (pos >= length) {
                break;
            }

            if (valueType == 1) {
                // Quoted string — can chain more extensions after closing quote
                buf[pos++] = '"';
                int valLen = 3 + rng.nextInt(12);
                for (int i = 0; i < valLen && pos < length - 1; i++) {
                    if (rng.nextInt(10) == 0 && pos < length - 2) {
                        buf[pos++] = '\\';
                        buf[pos++] = TOKEN_CHARS[rng.nextInt(TOKEN_CHARS.length)];
                    } else {
                        buf[pos++] = QDTEXT_CHARS[rng.nextInt(QDTEXT_CHARS.length)];
                    }
                }
                if (pos < length) {
                    buf[pos++] = '"';
                }
                // Can chain: ';' will correctly transition from QUOTED_END to CHUNK_EXT_NAME
            } else {
                // Token value — fill remaining space with token chars (no more ';' after this)
                while (pos < length) {
                    buf[pos++] = TOKEN_CHARS[rng.nextInt(TOKEN_CHARS.length)];
                }
                break;
            }
        }

        // Trim to exact length
        int finalLen = Math.min(pos, length);
        byte[] result = new byte[finalLen];
        System.arraycopy(buf, 0, result, 0, finalLen);

        // Trim back if we ended in an invalid state (e.g. mid-quoted-string)
        return trimToValidState(result);
    }

    /**
     * Trim trailing bytes until the sequence ends in a valid final state.
     */
    private static byte[] trimToValidState(byte[] data) {
        for (int len = data.length; len > 0; len--) {
            HttpChunkLineValidatingByteProcessor p = new HttpChunkLineValidatingByteProcessor();
            boolean ok = true;
            for (int i = 0; i < len; i++) {
                try {
                    p.process(data[i]);
                } catch (Exception e) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                try {
                    p.finish();
                    if (len == data.length) {
                        return data;
                    }
                    byte[] trimmed = new byte[len];
                    System.arraycopy(data, 0, trimmed, 0, len);
                    return trimmed;
                } catch (Exception e) {
                    // Not a valid final state, trim more
                }
            }
        }
        return new byte[]{ 'a', 'b', 'c', 'd' };
    }

    private int nextInput() {
        return (int) (next++ & (NUM_INPUTS - 1));
    }

    @Benchmark
    @CompilerControl(Mode.DONT_INLINE)
    public int flatTable() {
        byte[] data = inputs[nextInput()];
        flatProcessor.state = 0; // SIZE
        for (int i = 0, len = data.length; i < len; i++) {
            flatProcessor.process(data[i]);
        }
        return flatProcessor.state;
    }

    @Benchmark
    @CompilerControl(Mode.DONT_INLINE)
    public OldProcessor.State enumBitSet() {
        byte[] data = inputs[nextInput()];
        oldProcessor.state = OldProcessor.State.Size;
        for (int i = 0, len = data.length; i < len; i++) {
            oldProcessor.process(data[i]);
        }
        return oldProcessor.state;
    }

    // ---- Faithful copy of the old enum + Match[] implementation from upstream/4.2 ----

    static final class OldProcessor implements ByteProcessor {
        private static final int SIZE = 0;
        private static final int CHUNK_EXT_NAME = 1;
        private static final int CHUNK_EXT_VAL_START = 2;
        private static final int CHUNK_EXT_VAL_QUOTED = 3;
        private static final int CHUNK_EXT_VAL_QUOTED_ESCAPE = 4;
        private static final int CHUNK_EXT_VAL_QUOTED_END = 5;
        private static final int CHUNK_EXT_VAL_TOKEN = 6;

        static final class Match extends BitSet {
            private static final long serialVersionUID = 1L;
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

        enum State {
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
                            .chars("(),/:<=>?@[\\]{}", false)),
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
                            .chars("(),/:<=>?@[\\]{}", false),
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
                throw new IllegalStateException("Invalid byte");
            }
        }

        private static final State[] STATES_BY_ORDINAL = State.values();

        State state = State.Size;

        @Override
        public boolean process(byte value) {
            state = state.match(value);
            return true;
        }
    }
}
