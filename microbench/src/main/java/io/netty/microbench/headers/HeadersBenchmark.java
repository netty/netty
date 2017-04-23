/*
 * Copyright 2015 The Netty Project
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
package io.netty.microbench.headers;

import io.netty.handler.codec.Headers;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.AsciiString;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Threads(1)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class HeadersBenchmark extends AbstractMicrobenchmark {

    private static String toHttpName(String name) {
        return (name.startsWith(":")) ? name.substring(1) : name;
    }

    static String toHttp2Name(String name) {
        name = name.toLowerCase();
        return (name.equals("host")) ? "xhost" : name;
    }

    @Param
    ExampleHeaders.HeaderExample exampleHeader;

    AsciiString[] httpNames;
    AsciiString[] http2Names;
    AsciiString[] httpValues;

    DefaultHttpHeaders httpHeaders;
    DefaultHttp2Headers http2Headers;
    DefaultHttpHeaders emptyHttpHeaders;
    DefaultHttp2Headers emptyHttp2Headers;
    DefaultHttpHeaders emptyHttpHeadersNoValidate;
    DefaultHttp2Headers emptyHttp2HeadersNoValidate;
    SlowHeaders slowHttp2Headers;

    @Setup(Level.Trial)
    public void setup() {
        Map<String, String> headers = ExampleHeaders.EXAMPLES.get(exampleHeader);
        httpNames = new AsciiString[headers.size()];
        http2Names = new AsciiString[headers.size()];
        httpValues = new AsciiString[headers.size()];
        httpHeaders = new DefaultHttpHeaders(false);
        http2Headers = new DefaultHttp2Headers(false);
        int idx = 0;
        for (Map.Entry<String, String> header : headers.entrySet()) {
            String name = header.getKey();
            String httpName = toHttpName(name);
            String http2Name = toHttp2Name(name);
            String value = header.getValue();
            httpNames[idx] = new AsciiString(httpName);
            http2Names[idx] = new AsciiString(http2Name);
            httpValues[idx] = new AsciiString(value);
            httpHeaders.add(httpNames[idx], httpValues[idx]);
            http2Headers.add(http2Names[idx], httpValues[idx]);
            idx++;
        }
        slowHttp2Headers = new SlowHeaders(http2Headers);
        emptyHttpHeaders = new DefaultHttpHeaders(true);
        emptyHttp2Headers = new DefaultHttp2Headers(true);
        emptyHttpHeadersNoValidate = new DefaultHttpHeaders(false);
        emptyHttp2HeadersNoValidate = new DefaultHttp2Headers(false);
    }

    @Setup(Level.Invocation)
    public void setupEmptyHeaders() {
        emptyHttpHeaders.clear();
        emptyHttp2Headers .clear();
        emptyHttpHeadersNoValidate.clear();
        emptyHttp2HeadersNoValidate.clear();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void httpRemove(Blackhole bh) {
        for (AsciiString name : httpNames) {
            bh.consume(httpHeaders.remove(name));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void httpGet(Blackhole bh) {
        for (AsciiString name : httpNames) {
            bh.consume(httpHeaders.get(name));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public DefaultHttpHeaders httpPut() {
        DefaultHttpHeaders headers = new DefaultHttpHeaders(false);
        for (int i = 0; i < httpNames.length; i++) {
            headers.add(httpNames[i], httpValues[i]);
        }
        return headers;
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void httpIterate(Blackhole bh) {
        Iterator<Entry<CharSequence, CharSequence>> itr = httpHeaders.iteratorCharSequence();
        while (itr.hasNext()) {
            bh.consume(itr.next());
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void http2Remove(Blackhole bh) {
        for (AsciiString name : http2Names) {
            bh.consume(http2Headers.remove(name));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void http2Get(Blackhole bh) {
        for (AsciiString name : http2Names) {
            bh.consume(http2Headers.get(name));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public DefaultHttp2Headers http2Put() {
        DefaultHttp2Headers headers = new DefaultHttp2Headers(false);
        for (int i = 0; i < http2Names.length; i++) {
            headers.add(http2Names[i], httpValues[i]);
        }
        return headers;
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void http2Iterate(Blackhole bh) {
        for (Entry<CharSequence, CharSequence> entry : http2Headers) {
            bh.consume(entry);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void httpAddAllFastest(Blackhole bh) {
        bh.consume(emptyHttpHeadersNoValidate.add(httpHeaders));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void httpAddAllFast(Blackhole bh) {
        bh.consume(emptyHttpHeaders.add(httpHeaders));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void http2AddAllFastest(Blackhole bh) {
        bh.consume(emptyHttp2HeadersNoValidate.add(http2Headers));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void http2AddAllFast(Blackhole bh) {
        bh.consume(emptyHttp2Headers.add(http2Headers));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void http2AddAllSlow(Blackhole bh) {
        bh.consume(emptyHttp2Headers.add(slowHttp2Headers));
    }

    private static final class SlowHeaders implements Headers<CharSequence, CharSequence, SlowHeaders> {
        private final Headers<CharSequence, CharSequence, ? extends Headers<?, ?, ?>> delegate;
        private SlowHeaders(Headers<CharSequence, CharSequence, ? extends Headers<?, ?, ?>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public CharSequence get(CharSequence name) {
            return delegate.get(name);
        }

        @Override
        public CharSequence get(CharSequence name, CharSequence defaultValue) {
            return delegate.get(name, defaultValue);
        }

        @Override
        public CharSequence getAndRemove(CharSequence name) {
            return delegate.getAndRemove(name);
        }

        @Override
        public CharSequence getAndRemove(CharSequence name, CharSequence defaultValue) {
            return delegate.getAndRemove(name, defaultValue);
        }

        @Override
        public List<CharSequence> getAll(CharSequence name) {
            return delegate.getAll(name);
        }

        @Override
        public List<CharSequence> getAllAndRemove(CharSequence name) {
            return delegate.getAllAndRemove(name);
        }

        @Override
        public Boolean getBoolean(CharSequence name) {
            return delegate.getBoolean(name);
        }

        @Override
        public boolean getBoolean(CharSequence name, boolean defaultValue) {
            return delegate.getBoolean(name, defaultValue);
        }

        @Override
        public Byte getByte(CharSequence name) {
            return delegate.getByte(name);
        }

        @Override
        public byte getByte(CharSequence name, byte defaultValue) {
            return delegate.getByte(name, defaultValue);
        }

        @Override
        public Character getChar(CharSequence name) {
            return delegate.getChar(name);
        }

        @Override
        public char getChar(CharSequence name, char defaultValue) {
            return delegate.getChar(name, defaultValue);
        }

        @Override
        public Short getShort(CharSequence name) {
            return delegate.getShort(name);
        }

        @Override
        public short getShort(CharSequence name, short defaultValue) {
            return delegate.getShort(name, defaultValue);
        }

        @Override
        public Integer getInt(CharSequence name) {
            return delegate.getInt(name);
        }

        @Override
        public int getInt(CharSequence name, int defaultValue) {
            return delegate.getInt(name, defaultValue);
        }

        @Override
        public Long getLong(CharSequence name) {
            return delegate.getLong(name);
        }

        @Override
        public long getLong(CharSequence name, long defaultValue) {
            return delegate.getLong(name, defaultValue);
        }

        @Override
        public Float getFloat(CharSequence name) {
            return delegate.getFloat(name);
        }

        @Override
        public float getFloat(CharSequence name, float defaultValue) {
            return delegate.getFloat(name, defaultValue);
        }

        @Override
        public Double getDouble(CharSequence name) {
            return delegate.getDouble(name);
        }

        @Override
        public double getDouble(CharSequence name, double defaultValue) {
            return delegate.getDouble(name, defaultValue);
        }

        @Override
        public Long getTimeMillis(CharSequence name) {
            return delegate.getTimeMillis(name);
        }

        @Override
        public long getTimeMillis(CharSequence name, long defaultValue) {
            return delegate.getTimeMillis(name, defaultValue);
        }

        @Override
        public Boolean getBooleanAndRemove(CharSequence name) {
            return delegate.getBooleanAndRemove(name);
        }

        @Override
        public boolean getBooleanAndRemove(CharSequence name, boolean defaultValue) {
            return delegate.getBooleanAndRemove(name, defaultValue);
        }

        @Override
        public Byte getByteAndRemove(CharSequence name) {
            return delegate.getByteAndRemove(name);
        }

        @Override
        public byte getByteAndRemove(CharSequence name, byte defaultValue) {
            return delegate.getByteAndRemove(name, defaultValue);
        }

        @Override
        public Character getCharAndRemove(CharSequence name) {
            return delegate.getCharAndRemove(name);
        }

        @Override
        public char getCharAndRemove(CharSequence name, char defaultValue) {
            return delegate.getCharAndRemove(name, defaultValue);
        }

        @Override
        public Short getShortAndRemove(CharSequence name) {
            return delegate.getShortAndRemove(name);
        }

        @Override
        public short getShortAndRemove(CharSequence name, short defaultValue) {
            return delegate.getShortAndRemove(name, defaultValue);
        }

        @Override
        public Integer getIntAndRemove(CharSequence name) {
            return delegate.getIntAndRemove(name);
        }

        @Override
        public int getIntAndRemove(CharSequence name, int defaultValue) {
            return delegate.getIntAndRemove(name, defaultValue);
        }

        @Override
        public Long getLongAndRemove(CharSequence name) {
            return delegate.getLongAndRemove(name);
        }

        @Override
        public long getLongAndRemove(CharSequence name, long defaultValue) {
            return delegate.getLongAndRemove(name, defaultValue);
        }

        @Override
        public Float getFloatAndRemove(CharSequence name) {
            return delegate.getFloatAndRemove(name);
        }

        @Override
        public float getFloatAndRemove(CharSequence name, float defaultValue) {
            return delegate.getFloatAndRemove(name, defaultValue);
        }

        @Override
        public Double getDoubleAndRemove(CharSequence name) {
            return delegate.getDoubleAndRemove(name);
        }

        @Override
        public double getDoubleAndRemove(CharSequence name, double defaultValue) {
            return delegate.getDoubleAndRemove(name, defaultValue);
        }

        @Override
        public Long getTimeMillisAndRemove(CharSequence name) {
            return delegate.getTimeMillisAndRemove(name);
        }

        @Override
        public long getTimeMillisAndRemove(CharSequence name, long defaultValue) {
            return delegate.getTimeMillisAndRemove(name, defaultValue);
        }

        @Override
        public boolean contains(CharSequence name) {
            return delegate.contains(name);
        }

        @Override
        public boolean contains(CharSequence name, CharSequence value) {
            return delegate.contains(name, value);
        }

        @Override
        public boolean containsObject(CharSequence name, Object value) {
            return delegate.containsObject(name, value);
        }

        @Override
        public boolean containsBoolean(CharSequence name, boolean value) {
            return delegate.containsBoolean(name, value);
        }

        @Override
        public boolean containsByte(CharSequence name, byte value) {
            return delegate.containsByte(name, value);
        }

        @Override
        public boolean containsChar(CharSequence name, char value) {
            return delegate.containsChar(name, value);
        }

        @Override
        public boolean containsShort(CharSequence name, short value) {
            return delegate.containsShort(name, value);
        }

        @Override
        public boolean containsInt(CharSequence name, int value) {
            return delegate.containsInt(name, value);
        }

        @Override
        public boolean containsLong(CharSequence name, long value) {
            return delegate.containsLong(name, value);
        }

        @Override
        public boolean containsFloat(CharSequence name, float value) {
            return delegate.containsFloat(name, value);
        }

        @Override
        public boolean containsDouble(CharSequence name, double value) {
            return delegate.containsDouble(name, value);
        }

        @Override
        public boolean containsTimeMillis(CharSequence name, long value) {
            return delegate.containsTimeMillis(name, value);
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public Set<CharSequence> names() {
            return delegate.names();
        }

        @Override
        public SlowHeaders add(CharSequence name, CharSequence value) {
            delegate.add(name, value);
            return this;
        }

        @Override
        public SlowHeaders add(CharSequence name, Iterable<? extends CharSequence> values) {
            delegate.add(name, values);
            return this;
        }

        @Override
        public SlowHeaders add(CharSequence name, CharSequence... values) {
            delegate.add(name, values);
            return this;
        }

        @Override
        public SlowHeaders addObject(CharSequence name, Object value) {
            delegate.addObject(name, value);
            return this;
        }

        @Override
        public SlowHeaders addObject(CharSequence name, Iterable<?> values) {
            delegate.addObject(name, values);
            return this;
        }

        @Override
        public SlowHeaders addObject(CharSequence name, Object... values) {
            delegate.addObject(name, values);
            return this;
        }

        @Override
        public SlowHeaders addBoolean(CharSequence name, boolean value) {
            delegate.addBoolean(name, value);
            return this;
        }

        @Override
        public SlowHeaders addByte(CharSequence name, byte value) {
            delegate.addByte(name, value);
            return this;
        }

        @Override
        public SlowHeaders addChar(CharSequence name, char value) {
            delegate.addChar(name, value);
            return this;
        }

        @Override
        public SlowHeaders addShort(CharSequence name, short value) {
            delegate.addShort(name, value);
            return this;
        }

        @Override
        public SlowHeaders addInt(CharSequence name, int value) {
            delegate.addInt(name, value);
            return this;
        }

        @Override
        public SlowHeaders addLong(CharSequence name, long value) {
            delegate.addLong(name, value);
            return this;
        }

        @Override
        public SlowHeaders addFloat(CharSequence name, float value) {
            delegate.addFloat(name, value);
            return this;
        }

        @Override
        public SlowHeaders addDouble(CharSequence name, double value) {
            delegate.addDouble(name, value);
            return this;
        }

        @Override
        public SlowHeaders addTimeMillis(CharSequence name, long value) {
            delegate.addTimeMillis(name, value);
            return this;
        }

        @Override
        public SlowHeaders add(Headers<? extends CharSequence, ? extends CharSequence, ?> headers) {
            delegate.add(headers);
            return this;
        }

        @Override
        public SlowHeaders set(CharSequence name, CharSequence value) {
            delegate.set(name, value);
            return this;
        }

        @Override
        public SlowHeaders set(CharSequence name, Iterable<? extends CharSequence> values) {
            delegate.set(name, values);
            return this;
        }

        @Override
        public SlowHeaders set(CharSequence name, CharSequence... values) {
            delegate.set(name, values);
            return this;
        }

        @Override
        public SlowHeaders setObject(CharSequence name, Object value) {
            delegate.setObject(name, value);
            return this;
        }

        @Override
        public SlowHeaders setObject(CharSequence name, Iterable<?> values) {
            delegate.setObject(name, values);
            return this;
        }

        @Override
        public SlowHeaders setObject(CharSequence name, Object... values) {
            delegate.setObject(name, values);
            return this;
        }

        @Override
        public SlowHeaders setBoolean(CharSequence name, boolean value) {
            delegate.setBoolean(name, value);
            return this;
        }

        @Override
        public SlowHeaders setByte(CharSequence name, byte value) {
            delegate.setByte(name, value);
            return this;
        }

        @Override
        public SlowHeaders setChar(CharSequence name, char value) {
            delegate.setChar(name, value);
            return this;
        }

        @Override
        public SlowHeaders setShort(CharSequence name, short value) {
            delegate.setShort(name, value);
            return this;
        }

        @Override
        public SlowHeaders setInt(CharSequence name, int value) {
            delegate.setInt(name, value);
            return this;
        }

        @Override
        public SlowHeaders setLong(CharSequence name, long value) {
            delegate.setLong(name, value);
            return this;
        }

        @Override
        public SlowHeaders setFloat(CharSequence name, float value) {
            delegate.setFloat(name, value);
            return this;
        }

        @Override
        public SlowHeaders setDouble(CharSequence name, double value) {
            delegate.setDouble(name, value);
            return this;
        }

        @Override
        public SlowHeaders setTimeMillis(CharSequence name, long value) {
            delegate.setTimeMillis(name, value);
            return this;
        }

        @Override
        public SlowHeaders set(Headers<? extends CharSequence, ? extends CharSequence, ?> headers) {
            delegate.set(headers);
            return this;
        }

        @Override
        public SlowHeaders setAll(Headers<? extends CharSequence, ? extends CharSequence, ?> headers) {
            delegate.setAll(headers);
            return this;
        }

        @Override
        public boolean remove(CharSequence name) {
            return delegate.remove(name);
        }

        @Override
        public SlowHeaders clear() {
            delegate.clear();
            return this;
        }

        @Override
        public Iterator<Entry<CharSequence, CharSequence>> iterator() {
            return delegate.iterator();
        }
    }
}
