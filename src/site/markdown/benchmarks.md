# Benchmarks

Last update: December 2021. 

We conducted benchmarks and compared the following cache implementations:

- EHCache3 version 3.9.6
- Caffeine version 3.0.5
- Cache2k version 2.6.0.Final
  
Test environment:

- AMD EPYC 7401P 24-Core Processor, with SMT enabled, reporting 48 CPUs
- 256GB RAM
- Oracle Java 17 (build 17+35-LTS-2724), with no relevant tuning or limits
- Ubuntu 20.04
- JMH 1.33

For benchmarking JMH is used. Each benchmark runs with a iteration time of 10 seconds.
We run two iterations in three forks each to detect outliers. 

Benchmarks are run multiple times with different thread counts, cache sizes and
key ranges. The Java process is limited to the amount of CPU cores corresponding to
the thread count. Note that the highest thread count exceeds the number 
of physical CPU cores. 

The metrics we present are:

- runtime, for one shot benchmarks
- operations per second, for throughput benchmarks
- effective hitrate, calculated by the benchmarking framework
- resident set size high water mark, reported by the Linux OS
- live objects, via as reported via `jmap -histo:live`

Every bar chart has a confidence interval associated with it. This interval 
does not just represent the upper and lower bounds of a measured value, 
but it shows a range of potential values. Confidence interval is
calculated by JMH with a level of 99.9% (which means likelihood 
that the actual value is between the shown interval is 99.9%). A higher iteration time 
usually results in less result variance. We found that 10 seconds is a good compromise, 
keeping the runtime low and resulting in acceptable variance.

The benchmark uses integer keys and values, minimizing the memory that is used besides the
caching data structures. The benchmarks are designed to highlight differences between
caching implementations.

We keep interpretations of the results sparse and only comment on effects that may be 
overlooked.

## PopulateParallelOnce, One Shot Performance

The benchmark inserts entries in multiple threads up the entry capacity.
Each time a new cache is created, so potential hash table expansions are
part of the runtime. Link to the source code: 
[PopulateParallelOnceBenchmark.java](https://github.com/cache2k/cache2k-benchmark/blob/master/jmh-suite/src/main/java/org/cache2k/benchmark/jmh/cacheSuite/PopulateParallelOnceBenchmark.java)

The result is the achieved runtime, so a lower result is better. 

![](benchmark-result/PopulateParallelOnceBenchmark-byThreads-4M-notitle.svg)

*PopulateParallelOnceBenchmark, runtime by thread count for 4M cache size ([Alternative image](benchmark-result/PopulateParallelOnceBenchmark-byThreads-4M-notitle-print.svg), [Data file](benchmark-result/PopulateParallelOnceBenchmark-byThreads-4M.dat))*

## PopulateParallelTwice, One Shot Performance

The benchmark inserts entries in multiple threads stopping at twice of the entry capacity.
So one part of the benchmark is pure inserting, while the second part is inserting and
eviction. Link to the source code:
[PopulateParallelTwiceBenchmark.java](https://github.com/cache2k/cache2k-benchmark/blob/master/jmh-suite/src/main/java/org/cache2k/benchmark/jmh/cacheSuite/PopulateParallelTwiceBenchmark.java)

The result is the achieved runtime, so a lower result is better.

![](benchmark-result/PopulateParallelTwiceBenchmark-byThreads-4M-notitle.svg)

*PopulateParallelTwiceBenchmark, runtime by thread count for 4M cache size ([Alternative image](benchmark-result/PopulateParallelTwiceBenchmark-byThreads-4M-notitle-print.svg), [Data file](benchmark-result/PopulateParallelTwiceBenchmark-byThreads-4M.dat))*

## ZipfianSequenceLoading, Throughput Performance

The benchmark is doing requests with cache keys based on a Zipfian distribution.
The Zipfian distribution is a typical artificial skewed access sequence, meaning
that keys vary in their appearance from very often to very rare.
The generated key space of the Zipfian distribution is larger than the cache capacity
(110% and 500% of the cache entry capacity) which will cause evictions and cache misses.
The cache is operating in read through mode, which means for missing mappings the cache 
will invoke the loader. Each load operation is consuming some CPU time to simulate 
work for generating or loading the value and adding a miss penalty.

[ZipfianLoadingBenchmark.java](https://github.com/cache2k/cache2k-benchmark/blob/master/jmh-suite/src/main/java/org/cache2k/benchmark/jmh/cacheSuite/ZipfianSequenceLoadingBenchmark.java)

First we present the results for operations per seconds with a key range of 110%, meaning the Zipfian distribution generates
numbers between 0 and 1.100.000.

![](benchmark-result/ZipfianSequenceLoadingBenchmark-byThread-1Mx110-notitle.svg)

*ZipfianSequenceLoadingBenchmark, operations per second by thread count with cache size 1M and Zipfian percentage 110 ([Alternative image](benchmark-result/ZipfianSequenceLoadingBenchmark-byThread-1Mx110-notitle-print.svg), [Data file](benchmark-result/ZipfianSequenceLoadingBenchmark-byThread-1Mx110.dat))*

The resulting cache hit rate is very high and all caches are around 99.2% hit rate.

To produce lower hit rates and force the cache to do more eviction, we do another round of the same benchmark with 500% key range,
resulting in random numbers between 0 and 5.000.000.

![](benchmark-result/ZipfianSequenceLoadingBenchmark-byThread-1Mx500-notitle.svg)

*ZipfianSequenceLoadingBenchmark, operations per second by thread count with cache size 1M and Zipfian percentage 500 ([Alternative image](benchmark-result/ZipfianSequenceLoadingBenchmark-byThread-1Mx500-notitle-print.svg), [Data file](benchmark-result/ZipfianSequenceLoadingBenchmark-byThread-1Mx500.dat))*

Here are the resulting hit rates. The metric is calculated by the benchmark and does not 
depend on statistics reported by the cache. 

![](benchmark-result/ZipfianSequenceLoadingBenchmark-EffectiveHitrate-byThread-1Mx500-notitle.svg)

*ZipfianSequenceLoadingBenchmark, effective hit rate by thread count with cache size 1M  and Zipfian percentage 500 ([Alternative image](benchmark-result/ZipfianSequenceLoadingBenchmark-EffectiveHitrate-byThread-1Mx500-notitle-print.svg), [Data file](benchmark-result/ZipfianSequenceLoadingBenchmark-EffectiveHitrate-byThread-1Mx500.dat))*

Note that the hit rates of cache2k become better for more threads, while Caffeines' hit rates
degrade slightly.

Since caching libraries are used to manage memory resources efficiently it is important to
keep a close eye on memory usage of the library itself. So let us take a look at the memory usage:

![](benchmark-result/ZipfianSequenceLoadingBenchmark-Memory-4-1M-500-liveObjects-sorted-notitle.svg)

*ZipfianSequenceLoadingBenchmark, 4 threads, 1M cache entries, Zipfian distribution percentage 500, total bytes of live objects as reported by jmap ([Alternative image](benchmark-result/ZipfianSequenceLoadingBenchmarkMemory4-1M-500-liveObjects-sorted-notitle-print.svg), [Data file](benchmark-result/ZipfianSequenceLoadingBenchmarkMemory4-1M-500-liveObjects-sorted.dat))*

![](benchmark-result/ZipfianSequenceLoadingBenchmark-Memory-4-1M-500-VmHWM-sorted-notitle.svg)

*ZipfianSequenceLoadingBenchmark, 4 threads, 1M cache entries, Zipfian distribution percentage 500, peak memory usage reported by the operating system (VmHWM), sorted by best performance ([Alternative image](benchmark-result/ZipfianSequenceLoadingBenchmarkMemory4-1M-500-VmHWM-sorted-notitle-print.svg), [Data file](benchmark-result/ZipfianSequenceLoadingBenchmarkMemory4-1M-500-VmHWM-sorted.dat))*

The metric *live objects* represents the static view of bytes occupied by live objects in the heap 
without any additional runtime overhead, gathered via `jmap -histo:live`. 
The *VmHWM* metric represents the real memory used as reported by the Linux OS. 
The bigger differences in real memory usage are because of dynamic effects, especially higher 
amount of garbage collection activity.

For a discussion on how to measure memory usage, see: 
*[The 6 Memory Metrics You Should Track in Your Java Benchmarks](https://cruftex.net/2017/03/28/The-6-Memory-Metrics-You-Should-Track-in-Your-Java-Benchmarks.html)*

Since the used heap of cache2k is significantly lower, maybe a more fair benchmark would
work with different size limits across cache implementations to level the amount of used memory.
However, the difference would become less with relevant application data in the cache. The benchmark 
intentionally just uses integer objects.

## PopulateParallelClear, Throughput Performance

Each thread inserts unique keys up to 200% of the cache capacity in count.
After that, the first thread finishing issues a cache clear. After that the thread
starts with inserting again. In consequence, this benchmark is covering inserts, 
inserts and eviction and clear. A higher thread count is causing caches to be 
filled sooner after the clear, causing a higher ratio of inserts with eviction, 
so it is not doing an equal amount of work with more threads.

One goal of this benchmark to construct a throughput benchmark which covers inserts, 
that can run within a constant iteration time, rather than a one shot benchmark doing 
inserts with a varying runtime.

![](benchmark-result/PopulateParallelClearBenchmark-notitle.svg)

*PopulateParallelClearBenchmark, operations per second (complete) ([Alternative image](benchmark-result/PopulateParallelClearBenchmark-notitle-print.svg), [Data file](benchmark-result/PopulateParallelClearBenchmark.dat))*

## Eviction Performance

To test the eviction performance, we run a set of prerecorded access traces against the
cache implementations configured to different sizes. The caches use a different eviction algorithms.
The table shows the hitrate of the well known LRU algorithm in comparison to the hitrate 
of the cache implementations.

Remarks:

- EHCache3 is missing in this discipline since in this test the runtime is
  about 100x higher then for the other caches.
- Caffeine is configured to run the eviction in the same thread
  via `Caffeine.executor(Runnable::run)`. Otherwise the cache size limit would
  overshoot until the eviction thread runs, leading to false results.
- Cache2k is configured to not segment eviction data structures.
  Cache2k would usually split the eviction data structures depending on
  the CPU count to have better concurrent behavior.

Mind, that the result will differ under concurrent workloads. Our experiments 
show that (see above), with more active threads hit rates of cache2k improves 
because it is profiting from overlapping data accesses, while the hitrate 
of Caffeine degrades because of contended access stream buffers dropping 
arbitrary cache hits and a less accurate eviction in consequence.

Trace Name | Cache Size | Reference | Hitrate | Best | Hitrate | Diff | 2nd-best | Hitrate | Diff
---------- | ---------: | --------- | ------: | ---- | ------: | ---: | -------- | ------: | ---------:
financial1-1M | 12500 | lru | 37.98 | cache2k* | 40.04 | 2.06 | caffeine* | 39.04 | 1.06 |
financial1-1M | 25000 | lru | 44.63 | cache2k* | 52.64 | 8.01 | caffeine* | 44.62 | -0.01 |
financial1-1M | 50000 | lru | 45.61 | cache2k* | 54.35 | 8.74 | caffeine* | 49.92 | 4.31 |
financial1-1M | 100000 | lru | 54.62 | cache2k* | 54.59 | -0.03 | caffeine* | 53.84 | -0.79 |
financial1-1M | 200000 | lru | 55.48 | cache2k* | 54.79 | -0.69 | caffeine* | 54.39 | -1.08 |
scarab-recs | 25000 | lru | 67.71 | caffeine* | 69.97 | 2.26 | cache2k* | 68.78 | 1.07 |
scarab-recs | 50000 | lru | 75.49 | caffeine* | 75.76 | 0.28 | cache2k* | 74.23 | -1.25 |
scarab-recs | 75000 | lru | 79.42 | caffeine* | 78.81 | -0.61 | cache2k* | 77.31 | -2.11 |
scarab-recs | 100000 | lru | 81.77 | caffeine* | 80.41 | -1.36 | cache2k* | 79.42 | -2.35 |
loop | 256 | lru | 0.00 | cache2k* | 24.48 | 24.48 | caffeine* | 24.33 | 24.33 |
loop | 512 | lru | 0.00 | caffeine* | 49.47 | 49.47 | cache2k* | 48.96 | 48.96 |
zipf10K-1M | 500 | lru | 58.46 | cache2k* | 67.49 | 9.03 | caffeine* | 66.30 | 7.84 |
zipf10K-1M | 1000 | lru | 67.25 | cache2k* | 74.39 | 7.14 | caffeine* | 74.20 | 6.95 |
zipf10K-1M | 2000 | lru | 76.48 | caffeine* | 81.45 | 4.97 | cache2k* | 81.43 | 4.94 |
web12 | 200 | lru | 44.04 | caffeine* | 47.29 | 3.25 | cache2k* | 45.37 | 1.33 |
web12 | 300 | lru | 49.01 | cache2k* | 52.24 | 3.22 | caffeine* | 50.67 | 1.66 |
web12 | 400 | lru | 52.83 | caffeine* | 57.48 | 4.65 | cache2k* | 56.85 | 4.02 |
web12 | 800 | lru | 61.95 | cache2k* | 66.51 | 4.56 | caffeine* | 64.55 | 2.60 |
web12 | 1200 | lru | 66.85 | cache2k* | 70.55 | 3.70 | caffeine* | 68.84 | 1.99 |
web12 | 3000 | lru | 76.48 | cache2k* | 78.07 | 1.58 | caffeine* | 75.67 | -0.81 |
oltp | 2500 | lru | 45.07 | caffeine* | 48.62 | 3.55 | cache2k* | 46.71 | 1.64 |
oltp | 5000 | lru | 53.65 | caffeine* | 55.46 | 1.81 | cache2k* | 53.41 | -0.24 |
oltp | 10000 | lru | 60.70 | cache2k* | 60.64 | -0.07 | caffeine* | 59.40 | -1.31 |


The resulting hit rates of Caffeine and cache2k are typically better than LRU, but not in every case.
In some traces Caffeine does better, in other traces cache2k does better. This
investigation shows that, although the achieved throughput by cache2k is high, 
the eviction efficiency does not suffer when compared to LRU.

More information about the used traces can be found at: 
*[Java Caching Benchmarks 2016 - Part 2](https://cruftex.net/2016/05/09/Java-Caching-Benchmarks-2016-Part-2.html)*
