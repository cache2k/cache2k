## Todo

A poor mans issue tracker.

### Next

  * add lock spin exceeded to all spins
  * reset() dirty?
  * storage purge
  * purge: schedule a purge
  * purge: purge fullscan counter, purgedEntry counter...
  * Optimize purge: partial purge, start with least recently used
  * review isDirty() handling

### Warmups

  * Consistent exceptions on cache methods after close()
  * contains()
  * extract LRU operations from BaseCache

### Storage and persistence

  * passivation, entry dirty bit and storage aggregation
  * Test storage with .implementation(ClockProPlusCache.class), .implementation(ClockCache.class)
  * more than 2gb?
  * off-heap persistence
  * storage with MapDb, BabuDb, LevelDb
  * storage with rest interface?
  * flags for passivation and in-mem capacity, etc.
  * developer description for storage
  * Storage aggregation
  * async storage

### configuration

  * XML configuration
  * configuration templates / default configuration (e.g. for addPersistence())

### Prio A / for 1.0

  * API freeze, for usage as in-memory cache
  * improve documentation
  * make low overhead replacement algorithm standard (post LRU...)
  * improve thread usage and memory footprint for expiry timer
  * Rethink on cache manager, SPI, maybe take a look on jsr107 for this

  * Benchmark with more than one thread:
    Example: http://sourceforge.net/p/nitrocache/blog/2012/05/performance-benchmark-nitrocache--ehcache--infinispan--jcs--cach4j
    
### JSR107

Things in JSR107 we don't have yet.

  * Listeners
  * Factories for loader, expiry, etc.

#### Details

  * RefreshController: Rename? Add key!, what to do with exception?, 
    Use the CacheEntry instead of the long parameter list.
  * expiry/refresh: Implement sharp expiry and background refresh, refresh ahead of time with different now?
  * change cache size during operation: maximumSize / capacity JMX setting
  * explain/check null support
  * exceptions: stick to old data when intermediate exceptions occur
  * API: typing / K or ? extends K
  * API: typing for get(), see:    http://stackoverflow.com/questions/857420/what-are-the-reasons-why-map-getobject-key-is-not-fully-generic
  * API/implementation: transaction support for use as database cache, for the lock free cache implementations
  * API: destroy -> close
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
  * speedup locking? don't use synchronized, but we need volatile then. see:
    http://stackoverflow.com/questions/4633866/is-volatile-expensive
    http://lmax-exchange.github.io/disruptor/
    

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

#### Distributed caching

The interesting question is, what is the "core

  * GridGain
  * hazelcast
  * infinispan
  
### Marketing 

  * InfoQ
  * DZone
  * JavaCodeGeeks
  * java.net
  * reddit
  * javaranch caching article, at: http://www.coderanch.com/how-to/java/CachingStrategies
  * WikiPedia?

