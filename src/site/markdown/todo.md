## Todo

A poor mans issue tracker.

### tiny bits, just noted

  * ConfiguraitonOrCacheBuilder -> AnyBuilderBase
  * ConfigurationBuilder

### configuration

  * XML configuration
  * configuration templates / default configuration (e.g. for addPersistence())

### persistence

  * eviction call from memory cache!
  * marshaller registry / provider factory...
  * marshaller prioritization?
  * more than 2gb?
  * off-heap persistence
  * flags for passivation and in-mem capacity, etc.

### Prio A / for 1.0

  * API freeze, for usage as in-memory cache
  * improve documentation
  * make low overhead replacement algorithm standard (post LRU...)
  * improve thread usage and memory footprint for expiry timer
  * Rethink on cache manager, SPI, maybe take a look on jsr107 for this

  * Benchmark with more than one thead:
    Example: http://sourceforge.net/p/nitrocache/blog/2012/05/performance-benchmark-nitrocache--ehcache--infinispan--jcs--cach4j

#### Details

  * expiry/refresh: Implement sharp expiry and background refresh
  * change cache size during operation: maximumSize / capacity JMX setting
  * RefreshController: Rename? Add key!
  * explain/check null support
  * exceptions: stick to old data when intermediate exceptions occur
  * API: typing / K or ? extends K
  * API/implementation: transaction support for use as database cache, for the lock free cache implementations
  * expiry/refresh: explain behaviour and API description
  * final bulk source API
  * reorganize timer. currently one timer thread is used per cache.
  * optimize adaption of CP+
  * Remove ARC implementation from core package?
  * JMX support optional? Documentation?
  * noname caches/generated names and garbage collection?
  * special integer key variant
  * prefetch: correct implementation / don't increment usage / counter for evicted non-used entries?
  * clean API package / SPI for implementation?
  * getEntry()
  * remove commons-logging dependency?
  * Memory size estimation, check this:
    * http://codespot.net/2012/01/04/measuring-java-object-sizes/?relatedposts_exclude=382
    * http://marxsoftware.blogspot.de/2011/12/estimating-java-object-sizes-with.html
    * http://stackoverflow.com/questions/690805/any-java-caches-that-can-limit-memory-usage-of-in-memory-cache-not-just-instanc?rq=1
  * cache feature comparison: e.g.ehcache synchronuous write
  * exceptions: fetchExceptions counter in statistic!

### Prio B

  * jcache / jsr107 support?
  * single value cache
  * separate thread for background refresh?
  * JMX support for background refresh thread pool?
  * API: nice and cleaned bulk interface to API
  * auto sizing/tuning
  * add size estimation explain the size estimation

### Prio C

  * implement maintenance to shrink hash table size?

### Things to look on

  * GridGain
  * hazelcast
