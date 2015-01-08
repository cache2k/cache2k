## Todo

A poor mans issue tracker.

### 0.20

  * documentation update?
  
### 0.21

  * contains()
  * Closable, close() and closeAsync()

### 0.22

API cleanup

Planned API breaking changes in this release:
  
  * void remove(key) -> boolean remove(key)
  * remove deprecated methods

### Next

  * New benchmark
  * Statistics: removeCnt with storage is "wrong", there are heap entries for loaded/checked storage
  * test exceptions within expiry policy
  * stress test with evictions
  * description for exception handling: policy, exception counter, suppressed exceptions, exceptions and bulk requests
  * Better formatting: msecs/fetch=833.2831546786805
  * have totalFetchMillis and totalFetches as JMX value
  * peekEntry/iterator, make sure the entry values are consistent/immutable. modification time and value may be updated independently
  * Flushable, flush() and flushAsync()
  * clear() and clearAsync() ?
  * purge() and purgeAsync() ?

### Warmups

  * factor out triggered job
  * contains()
  * extract LRU operations from BaseCache

### Storage and persistence

  * fetchWithStorage, get rid of flag
  * reset() dirty?, review isDirty() handling
  * purge thread for timer thread decoupling
  * storage: special marshallers for int, long, string
  * Optimize purge: partial purge, start with least recently used
  * storage entryExpireTime -> int
  * storage: statistics counters
  * file storage: more than one marshaller                                                                                                                                                                                                                                                                                                                                          
  * purge: schedule a purge
  * purge: purge fullscan counter, purgedEntry counter...
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

### robustness

  * CP+: Why too much entries: size=4003, maxSize=4000

fetchesInFlight, does not go up consistently for refreshes. Also should count all entries that
do I/O with the storage.

tests with a faulty storage? disable() working in all conditions?

Multi threaded tests with storage, to make sure no entry operation takes place.

WARNING: exception during clear
java.lang.IllegalStateException: detected operations while clearing.
	at org.cache2k.storage.ImageFileStorage.clear(ImageFileStorage.java:305)
	at org.cache2k.impl.PassingStorageAdapter$10.call(PassingStorageAdapter.java:960)
	at org.cache2k.impl.PassingStorageAdapter$10.call(PassingStorageAdapter.java:942)
	at org.cache2k.impl.threading.GlobalPooledExecutor$ExecutorThread.run(GlobalPooledExecutor.java:318)
	at java.lang.Thread.run(Thread.java:744)

destroy(): we need to consistently check if cache is in shutdown phase after obtaining the lock.
there may be fetches or storage operations ongoing. E.g.:

WARNING: Refresh exception
java.lang.IllegalStateException: Timer already cancelled.
	at java.util.Timer.sched(Timer.java:397)
	at java.util.Timer.schedule(Timer.java:208)
	at org.cache2k.impl.BaseCache.insert(BaseCache.java:2074)
	at org.cache2k.impl.BaseCache.insert(BaseCache.java:2001)
	at org.cache2k.impl.BaseCache.insertFetched(BaseCache.java:1977)
	at org.cache2k.impl.BaseCache.fetchFromSource(BaseCache.java:1973)
	at org.cache2k.impl.BaseCache.fetchWithStorage(BaseCache.java:1868)
	at org.cache2k.impl.BaseCache.fetch(BaseCache.java:1849)
	at org.cache2k.impl.BaseCache$8.run(BaseCache.java:2167)
	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1145)
	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:615)
	at java.lang.Thread.run(Thread.java:744)

### configuration

  * XML configuration
  * configuration templates / default configuration (e.g. for addPersistence())

### Prio A / for 1.0

  * Review JavaDocs, switch to markdown in javadoc? interesting:  http://plugins.jetbrains.com/plugin/7253?pr=idea
  * API freeze, for usage as in-memory cache
  * improve documentation
  * make low overhead replacement algorithm standard (post LRU...)
  * improve thread usage and memory footprint for expiry timer
  * Rethink on cache manager, SPI, maybe take a look on jsr107 for this

  * Benchmark with more than one thread:
    Example: http://sourceforge.net/p/nitrocache/blog/2012/05/performance-benchmark-nitrocache--ehcache--infinispan--jcs--cach4j
    
### JSR107

Things in JSR107 we don't have yet.

  * class loader support
  * we need XML configuration, since JCache does not define a complete cache configuration  
  * Listeners
  * Factories for loader, expiry, etc.

#### Details

  * expiry/refresh: Implement sharp expiry and background refresh, refresh ahead of time with different now?
  * change cache size during operation: maximumSize / capacity JMX setting
  * explain/check null support
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
  * getEntry()
  * Memory size estimation, check this:
    * http://codespot.net/2012/01/04/measuring-java-object-sizes/?relatedposts_exclude=382
    * http://marxsoftware.blogspot.de/2011/12/estimating-java-object-sizes-with.html
    * http://stackoverflow.com/questions/690805/any-java-caches-that-can-limit-memory-usage-of-in-memory-cache-not-just-instanc?rq=1
  * cache feature comparison: e.g.ehcache synchronuous write
  * speedup locking? don't use synchronized, but we need volatile then. see:
    http://stackoverflow.com/questions/4633866/is-volatile-expensive
    http://lmax-exchange.github.io/disruptor/
  * cpu cache line: http://openjdk.java.net/jeps/142

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
  * ycombinator
  * javaranch caching article, at: http://www.coderanch.com/how-to/java/CachingStrategies
  * WikiPedia?
