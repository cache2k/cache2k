# Benchmarks

*Update: Meanwhile these benchmarks are two years old. The benchmarks are currently improved
and extended. This page will be updated when some useful and complete state is reached.
The latest development can be found at Jens' blog, starting here:
[Java Caching Benchmarks 2016 - Part 1](http://cruftex.net/2016/03/16/Java-Caching-Benchmarks-2016-Part-1.html)*

Here are some benchmarks. But let's start with the disclaimer: *There are lies,
damn lies and benchmarks!* The benchmarks as well as the cache2k cache algorithms
are still under heavy development. The code to reproduce the benchmarks
is in the [cache2k-benchmark package](https://github.com/headissue/cache2k-benchmark)
on GitHub.

## Comparison with other products

### The caches

Besides the current cache2k implementation(s) the following Java caches are used:

   * EHCache Version 2.7.2, uses probabilistic LRU eviction
   * Google Guava Cache 15.0, uses LRU eviction
   * Infinispan 6.0.0.Final, uses the LIRS eviction by default (see [LIRS])

The benchmark configuration is with expiry turned off, and binary storage or
store by value turned off. Everything else is default.

### cache2k algorithms

Within cache2k conventional and more recent cache eviction algorithms are implemented.

ARC is a modern adaptive algorithm (see [ARC]). In case of a cache hit it needs
the same list operations like LRU or LIRS. The other recent algorithm is CLOCK-Pro
which needs, as CLOCK does, no data structure operations on a cache hit and can therefore
be implemented lock free. The cache2k CLOCK-Pro implementation is not exactly as
presented in the paper (see [CP]), however, it is an adapted version which tries
to fit best with the needs of a Java object cache. We call this version "CLOCK-Pro Plus"
or CP+. The CP+ implementation is not finalized yet, but still evolving.

### The runtime benchmarks

In the first benchmark we measure the runtime of a specific task and compare the
different cache implementations. Each task is a run through a synthetic access trace.
Here is the description of these traces:

   * **Eff90**: Random distribution within 0 and 999, which yields about
      90% hitrate with LRU on a cache with maximum 500 elements.
   * **Eff95**: Random distribution within 0 and 999, which yields about
      95% hitrate with LRU on a cache with maximum 500 elements.
   * **Miss**: Counting sequence which yields no hits at all.
   * **Hits**: Repeating sequence between 0 and 499, which yields only hits
      after the first round.
   * **Random**: Randomly distributed sequence between 0 and 999.

Each benchmark run does three million cache requests (not concurrently) on a cache
with 500 maximum elements. There is no penalty to actually provide the cached data in case
of a cache miss. This benchmark focuses only on the internal cache overhead.

### Runtime comparison

The following comparison is with expiry switched off, this means all data may be cached
for ever.

![Runtime comparison with other cache products](benchmark-result/3ptySpeed.svg)

For the raw data, see [benchmark-result/3ptySpeed.dat](benchmark-result/3ptySpeed.dat).

### Runtime comparison with expiry enabled

The following comparison is with expiry configured to five minutes. Actually,
during the benchmark run not a single element will expire, however, it makes
a difference because (in the case of cache2k) timer data structures need
to be updated when an element is inserted or evicted.

![Runtime comparison with other cache products](benchmark-result/3ptySpeedWithExpiry.svg)

For the raw data, see [benchmark-result/3ptySpeedWithExpiry.dat](benchmark-result/3ptySpeedWithExpiry.dat).

Within cache2k there is room for improvement, reorganization of the timer
code is on the todo list.

### Hitrate comparison

For completeness here is the hitrate comparison.

![Runtime comparison with other cache products](benchmark-result/3ptyHitrate.svg)

For the raw data, see [benchmark-result/3ptyHitrate.dat](benchmark-result/3ptyHitrate.dat).

Interestingly there are some differences in the achieved hitrates.
Infinispan and Guava seem to interpret the maximum count of elements very
loose, so they yield a low hitrate when it should be 99.9%. See discussion on this below.


### Runtime comparison for hit hits

In the following diagram we look only at the runtime of a trace with mostly cache hits.
The "HashMap+Counter" is for comparing the results to a simple Java HashMap. This
"cache" fetches the data from a HashMap and increases a hit counter.

![Runtime comparison of 3 million cache hits](benchmark-result/speedHits.svg)

For the raw data, see [benchmark-result/speedHits.dat](benchmark-result/speedHits.dat).

## Eviction algorithm comparison

Now we compare the efficiency of cache eviction algorithm on real-world traces.
For reference there are two additional algorithms: OPT is the optimal achievable
hitrate according to Beladys offline algorithm (See [Belady]). The RAND
algorithm selects an entry randomly for eviction. To have stable readings a
pseudo random generator is used. To make sure the random selection does not favor
a specific trace, the "RAND" value is the average between three runs with different
random seeds. With OPT and RAND we have a meaningful upper and lower bound to compare
the cache efficiency of the different implementations.

### Web12 trace

Webserver access trace on detail pages to events (music, theater, etc.) in
munich. The trace was recorded in december 2012. The trace is a long-tail distribution
which may be typical for retail shops.

![hitrate comparison for Web12 trace](benchmark-result/traceWeb12hitrate.svg)

### Cpp trace

Reference trace, it was used in the CLOCK-Pro and LIRS papers (see [CP] and [LIRS].
Short description from CLOCK-Pro paper: cpp is a GNU C compiler pre-processor trace.
The total size of C source programs used as input is roughly 11 MB. The trace is a
member of the probabilistic pattern group.

The trace originated from the authors of the paper [UBM] and [LFRU].

![hitrate comparison for Cpp trace](benchmark-result/traceCpphitrate.svg)

### Sprite trace

Reference trace, it was used in the CLOCK-Pro and LIRS papers (see [CP] and [LIRS].
Short description from CLOCK-Pro paper: Sprite is from the Sprite network file system,
which contains requests to a file server from client workstations for a two-day period.
The trace is a member of the temporally-clustered pattern group.

The trace originated from the authors of the paper [UBM] and [LFRU].

![hitrate comparison for Sprite trace](benchmark-result/traceSpritehitrate.svg)

### Multi2 trace

Reference trace, it was used in the CLOCK-Pro and LIRS papers (see [CP] and [LIRS]).
Short description from CLOCK-Pro paper: multi2 is obtained by executing
three workloads, cs, cpp, and postgres, together. The trace is a member
of the mixed pattern group.

The trace originated from the authors of the paper [UBM] and [LFRU].

![hitrate comparison for Multi2 trace](benchmark-result/traceMulti2hitrate.svg)

### Remarks

The bad hitrate of **Google Guava** is caused by the fact that the cache starts evicting
elements before the maximum size is reached. This behaviour is documented on
`CacheBuilder.maximumSize()`:

> Note that the cache may evict an entry before this limit is
exceeded. As the cache size grows close to the maximum, the cache evicts entries that are
less likely to be used again. For example, the cache may evict an
entry because it hasn't been used recently or very often.

The test BenchmarkCollection.testSize1000() verifies this behaviour.

**Infinispan** uses an adaptive eviction algorithm (LIRS) by default, but the eviction
starts too early to really have a comparison for the algorithm itself. The cache, decides
to evict elements before the maximum cache size is reached.
The documentation on the parameter `EvictionConfigurationBuilder.maxEntries()` says:

> Maximum number of entries in a cache instance. Cache size is guaranteed not to exceed upper
limit specified by max entries. However, due to the nature of eviction it is unlikely to ever
be exactly maximum number of entries specified here.

Having or not having a configuration parameter to control the maximum cache size and how to
react on it is a lengthy discussion in its own. Anyway, an eviction that evicts entries
before it needs to, yields a bad caching efficiency.

## About the benchmarks

Here is a little more background on the benchmarks. The ultimate truth
can be found in the source on GitHub. You can find it in the
[cache2k-benchmark package](https://github.com/headissue/cache2k-benchmark).

All cache tests are carried out with simple integer values and keys. We
assume that the overhead for passing the objects is the same within the cache
implementations. However, more complex key implementations could make a
difference in runtime depending on how often hashCode() and equals() is called
by the cache implementation. Copying of values and keys or binary storage
of them is switched off, because we only want to benchmark the in-memory performance
not the marshalling and unmarshalling performance.

The runtime is measured with JUnitBenchmarks. The result is in seconds with
tree digits after comma, giving us a 1 millisecond resolution. Each runtime test
is carried out in five rounds, two for warmup and three for the benchmark
measurement.

The accuracy of is not optimal. The runtime of the fastest hit benchmarks is
within 50 to 70 milliseconds.

The test platform is:

  * Lenovo X220, with Intel(R) Core(TM) i7-2620M CPU @ 2.70GHz
  * Oracle Java 1.7.0_25 with HotSpot 64-Bit server VM

## Does a faster cache help for my application?

It depends on the cache hitrate and the penalty to fetch the data. The more often
you need to fetch the data, meaning you have a cache miss, and the more expansive
it is to do so, the less interesting is the the cache overhead itself.

Application architecture and the cache speed influence each other. To
meet performance requirements of an application it is possible to
reduce the cache access count by caching bigger components, such as
completely rendered HTML pages. So, OTOH with a faster cache more fine
grained caching is possible, allowing higher
application complexity and/or lowering the memory demands.

## More benchmarks?

Some ideas what should be covered by additional benchmarks:

  * More traces
  * Benchmark on server hardware in general
  * Multithreaded benchmark on a 8 core CPU
  * Run the benchmarks on different JVMs?

## References

  * [Belady] L. A. Belady, "A Study of Replacement Algorithms for Virtual Storage",
    *IBM System Journal, 1966.*
  * [UBM] J. Kim, J. Choi, J. Kim, S. Noh, S. Min, Y. Cho, and C. Kim,
    "A Low-Overhead, High-Performance Unified Buffer Management Scheme
    that Exploits Sequential and Looping References",
    *4th Symposium on Operating System Design & Implementation, October 2000.*
  * [LFRU] D. Lee, J. Choi, J. Kim, S. Noh, S. Min, Y. Cho and C. Kim,
    "On the Existence of a Spectrum of Policies that Subsumes the Least Recently Used
     (LRU) and Least Frequently Used (LFU) Policies", *Proceeding of 1999 ACM
     SIGMETRICS Conference, May 1999.*
  * [CP] Song Jiang, Feng Chen, Xiaodong Zhang.
    "CLOCK-Pro: an effective improvement of the CLOCK replacement",
    *Proceedings of the annual conference on USENIX Annual Technical Conference (2005)*
  * [LIRS] Song Jiang, and Xiaodong Zhang,
    "LIRS: An Efficient Low Inter-Reference Recency Set Replacement Policy to Improve
     Buffer Cache Performance", in *Proceedings of the ACM SIGMETRICS Conference on Measurement
     and Modeling of Computer Systems (SIGMETRICS'02)*





