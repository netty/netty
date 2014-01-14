# Netty Microbenchmarking Suite

The Netty Microbenchmarking Suite builds on top of [OpenJDK JMH](http://openjdk.java.net/projects/code-tools/jmh/), the preferred microbenchmarking solution for HotSpot. It has the "batteries included", so you don't need extra dependencies to get started.

## Running the Benchmarks
You can run the benchmarks from the command line through maven or directly in your IDE. To run all tests with the default settings, use `mvn -DskipTests=false test`. You need to explicitly set `skipTests=false` because we don't want to run the (potentially time consuming) microbenchmarks to be executed as unit tests during regular test runs.

If all goes well, you'll see JMH performing warmup and benchmark iterations on the number of forks, presenting you with nice a nice summary. Here's how a typical benchmark run looks like (you'll see lots of them in the output):

```
# Fork: 2 of 2
# Warmup: 10 iterations, 1 s each
# Measurement: 10 iterations, 1 s each
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Throughput, ops/time
# Running: io.netty.microbench.buffer.ByteBufAllocatorBenchmark.pooledDirectAllocAndFree_1_0
# Warmup Iteration   1: 8454.103 ops/ms
# Warmup Iteration   2: 11551.524 ops/ms
# Warmup Iteration   3: 11677.575 ops/ms
# Warmup Iteration   4: 11404.954 ops/ms
# Warmup Iteration   5: 11553.299 ops/ms
# Warmup Iteration   6: 11514.766 ops/ms
# Warmup Iteration   7: 11661.768 ops/ms
# Warmup Iteration   8: 11667.577 ops/ms
# Warmup Iteration   9: 11551.240 ops/ms
# Warmup Iteration  10: 11692.991 ops/ms
Iteration   1: 11633.877 ops/ms
Iteration   2: 11740.063 ops/ms
Iteration   3: 11751.798 ops/ms
Iteration   4: 11260.071 ops/ms
Iteration   5: 11461.010 ops/ms
Iteration   6: 11642.912 ops/ms
Iteration   7: 11808.595 ops/ms
Iteration   8: 11683.780 ops/ms
Iteration   9: 11750.292 ops/ms
Iteration  10: 11769.986 ops/ms

Result : 11650.238 ±(99.9%) 229.698 ops/ms
  Statistics: (min, avg, max) = (11260.071, 11650.238, 11808.595), stdev = 169.080
  Confidence interval (99.9%): [11420.540, 11879.937]
```

Finally, the test output will looks similar to this (depending on your system setup and configuration):

```
Benchmark                                                                Mode   Samples         Mean   Mean error    Units
i.n.m.b.ByteBufAllocatorBenchmark.pooledDirectAllocAndFree_1_0          thrpt        20    11658.812      120.728   ops/ms
i.n.m.b.ByteBufAllocatorBenchmark.pooledDirectAllocAndFree_2_256        thrpt        20    10308.626      147.528   ops/ms
i.n.m.b.ByteBufAllocatorBenchmark.pooledDirectAllocAndFree_3_1024       thrpt        20     8855.815       55.933   ops/ms
i.n.m.b.ByteBufAllocatorBenchmark.pooledDirectAllocAndFree_4_4096       thrpt        20     5545.538     1279.721   ops/ms
i.n.m.b.ByteBufAllocatorBenchmark.pooledDirectAllocAndFree_5_16384      thrpt        20     6741.581       75.975   ops/ms
i.n.m.b.ByteBufAllocatorBenchmark.pooledDirectAllocAndFree_6_65536      thrpt        20     7252.869       70.609   ops/ms
i.n.m.b.ByteBufAllocatorBenchmark.pooledHeapAllocAndFree_1_0            thrpt        20     9750.225       73.900   ops/ms
i.n.m.b.ByteBufAllocatorBenchmark.pooledHeapAllocAndFree_2_256          thrpt        20     9936.639      657.818   ops/ms
i.n.m.b.ByteBufAllocatorBenchmark.pooledHeapAllocAndFree_3_1024         thrpt        20     8903.130      197.533   ops/ms
i.n.m.b.ByteBufAllocatorBenchmark.pooledHeapAllocAndFree_4_4096         thrpt        20     6664.157       74.163   ops/ms
i.n.m.b.ByteBufAllocatorBenchmark.pooledHeapAllocAndFree_5_16384        thrpt        20     6374.924      337.869   ops/ms
i.n.m.b.ByteBufAllocatorBenchmark.pooledHeapAllocAndFree_6_65536        thrpt        20     6386.337       44.960   ops/ms
i.n.m.b.ByteBufAllocatorBenchmark.unpooledDirectAllocAndFree_1_0        thrpt        20     2137.241       30.792   ops/ms
i.n.m.b.ByteBufAllocatorBenchmark.unpooledDirectAllocAndFree_2_256      thrpt        20     1873.727       41.843   ops/ms
i.n.m.b.ByteBufAllocatorBenchmark.unpooledDirectAllocAndFree_3_1024     thrpt        20     1902.025       34.473   ops/ms
i.n.m.b.ByteBufAllocatorBenchmark.unpooledDirectAllocAndFree_4_4096     thrpt        20     1534.347       20.509   ops/ms
i.n.m.b.ByteBufAllocatorBenchmark.unpooledDirectAllocAndFree_5_16384    thrpt        20      838.804       12.575   ops/ms
i.n.m.b.ByteBufAllocatorBenchmark.unpooledDirectAllocAndFree_6_65536    thrpt        20      276.976        3.021   ops/ms
i.n.m.b.ByteBufAllocatorBenchmark.unpooledHeapAllocAndFree_1_0          thrpt        20    35820.568      259.187   ops/ms
i.n.m.b.ByteBufAllocatorBenchmark.unpooledHeapAllocAndFree_2_256        thrpt        20    19660.951      295.012   ops/ms
i.n.m.b.ByteBufAllocatorBenchmark.unpooledHeapAllocAndFree_3_1024       thrpt        20     6264.614       77.704   ops/ms
i.n.m.b.ByteBufAllocatorBenchmark.unpooledHeapAllocAndFree_4_4096       thrpt        20     2921.598       95.492   ops/ms
i.n.m.b.ByteBufAllocatorBenchmark.unpooledHeapAllocAndFree_5_16384      thrpt        20      991.631       49.220   ops/ms
i.n.m.b.ByteBufAllocatorBenchmark.unpooledHeapAllocAndFree_6_65536      thrpt        20      261.718       11.108   ops/ms
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 993.382 sec - in io.netty.microbench.buffer.ByteBufAllocatorBenchmark
```

You can also run the benchmarks directly from your IDE. If you've imported the netty parent project, open the `microbench` subproject and navigate to the `src/test/java/io/netty/microbench` namespace. In the `buffer` namespace, you can run the `ByteBufAllocatorBenchmark` like any other JUnit-based test. The main difference is that (as of now), you can only run the full benchmark at once, not each sub-benchmark individually. You should see the same output in the console as you did see when running it directly through `mvn`.

## Writing Benchmarks
Writing the benchmark itself is not hard, but getting it right is. This not because the microbench project is difficult to use, but more because you need to avoid common pitfalls when writing them. Thankfully, the JMH suite provides helpful annotations and features to mitigate most of them. To get started, you need to make your benchmark extend the `AbstractMicrobenchmark`, which makes sure the test gets run through JUnit and configures some defaults:

```java
public class MyBenchmark extends AbstractMicrobenchmark {

}
```

The next step is to create a method which is annotated with `@GenerateMicroBenchmark` (and give it a descriptive name):

```java
@GenerateMicroBenchmark
public void measureSomethingHere() {

}
```

The best idea now is to look [here](http://hg.openjdk.java.net/code-tools/jmh/file/tip/jmh-samples/src/main/java/org/openjdk/jmh/samples/) for samples and inspiration on how to write proper JMH tests. Also, check out [the talks](http://shipilev.net/#benchmarking) of one of the main authors from JMH.

## Customizing Runtime Conditions
The default settings (as found in `AbstractMicrobenchmark`) are:

 - Warmup Iterations: 10
 - Measure Iterations: 10
 - Number of Forks: 2

These settings can be customized through system properties at runtime (`warmupIterations`, `measureIterations` and `forks`):

```
mvn -DskipTests=false -DwarmupIterations=2 -DmeasureIterations=3 -Dforks=1 test
```

Note that it is generally not advised to use that few iterations, but it sometimes is helpful to see if the benchmark works and then run comprehensive benchmarks at a later point.

Note that you can also customize those default settings on a per-test basis through annotations:

```java
@Warmup(iterations = 20)
@Fork(1)
public class MyBenchmark extends AbstractMicrobenchmark {

}
```

This can be done on a per-class and per-method (benchmark) basis. Note that command line arguments always override the annotation defaults.
