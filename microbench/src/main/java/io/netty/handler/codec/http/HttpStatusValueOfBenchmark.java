package io.netty.handler.codec.http;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.internal.SuppressJava6Requirement;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Warmup;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class HttpStatusValueOfBenchmark extends AbstractMicrobenchmark {

    private int[] data;
    private HttpStatusClass[] result;
    @Param({ "25", "32", "63", "95" })
    public int size;

    @Setup(Level.Iteration)
    @SuppressJava6Requirement(reason = "suppress")
    public void setup() {
        final SplittableRandom random = new SplittableRandom();
        data = new int[size];
        result = new HttpStatusClass[size];
        for (int j = 0; j < size; j++) {
            data[j] = random.nextInt(100, 700);
        }
    }

    @Benchmark
    public HttpStatusClass[] ofValue() {
        for (int i = 0; i < size; ++i) {
            result[i] = HttpStatusClass.valueOf(data[i]);
        }
        return result;
    }

}