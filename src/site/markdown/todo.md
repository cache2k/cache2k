## Todo

A poor mans issue tracker.

### Prio A / for 1.0

  * explain/check null support
  * exceptions: stick to old data when intermediate exceptions occur
  * API: typing / K or ? extends K
  * API/implementation: transaction support for use as database cache, for the lock free cache implementations
  * expiry/refresh: explain behaviour and API description
  * expiry/refresh: Implement sharp expiry
  * final bulk source API
  * reorganize timer. currently one timer thread is used per cache.
  * optimize adaption of CP+
  * Remove ARC implementation from core package?
  * JMX support
  * noname caches/generated names and garbage collection?
  * special integer key variant
  * prefetch: correct implementation
  * clean API package / SPI for implementation?

### Prio B


  * single value cache
  * separate thread for background refresh?
  * JMX support for background refresh thread pool?
  * API: nice and cleaned bulk interface to API
  * auto sizing/tuning
  * add size estimation explain the size estimation

### Prio C

  * implement maintenance to shrink hash table size?
