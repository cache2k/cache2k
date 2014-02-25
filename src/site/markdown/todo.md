## Todo

A poor mans issue tracker.

### Prio A / for 1.0

  * API freeze, for usage as in-memory cache
  * improve documentation
  * make low overhead replacement algorithm standard (post LRU...)
  * improve thread usage and memory footprint for expiry timer
  * Rethink on cache manager, SPI, maybe take a look on jsr107 for this

#### Details

  * expiry/refresh: Implement sharp expiry and background refresh
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
