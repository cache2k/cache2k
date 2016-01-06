# cache2k version 0.24 release notes

## Potential breakages

Changes in semantics or API that may break existing applications are listed here. In general, only very minor
changes are done with breaking existing semantics, which will most likely not affect existing applications.
Everything that will most likely break applications will be introduced as new API and the old will get deprecated.
Modifications in the statistics output will not listed as breakage.

  * Semantics of Cache.getAll() changed. Instead of returning always a map size equal to the requested count of keys,
    only keys with a non-null mapping are returned in the map.
  * Added generic types to the methods CacheBuilder.entryExpiryCalculator and CacheBuilder.exceptionExpiryCalculator

## Bug fixes

If something is listed here it might affect an existing application and updating is recommended.

  * Enabled background refresh and entry expiry calculator: In case the calculator returned the current time or a past time, the 
    entry was not marked as expired.
  * Fix possible race condition in cache manager when adding and closing caches and requesting an iteration of the existing caches
  * Retrieving existing entries via peek() might return null if a concurrent put() happens

## New and Noteworthy

  TODO: check API, add since, add comment!
  * entry processor scheme like in JSR107: Cache.invokeAll,
  * Beginning of JSR107 support.

## 

## Fixes and Improvements

Fixes of corner cases that are most likely not affecting any existing applications and improvements are listed here.
  
  * Performance improvement: put() on existing entry by 15%
  * Typing: "unsupported" exception if an array is used for key or value types
  * Typing: Actual type parameters of genreic types can be stored in the cache config
  * Typing: Converted CacheConfig to a generic type, transporting the key/value types at compile time
  * The cache manager logs the used default cache implementation at startup
  * Performance improvement: read access and cache hit (approx. 5%) on 64 bit JVMs
  * Cache.iterator(): Proper exception on wrong usage of iterator pattern
  * Cache.iterator(): Fix semantics for direct call to next() without call to hasNext()
  * Handle exceptions within the expiry calculator more gracefully (still needs work, entry state is inconsistent if an exceptions happens here)
  * ExceptionPropagator for customising the propagation of cached exception

Statistics:

  * Cache.contains(), does no access recording if no storage is attached. Change because JSR107 specifies that a contains() does 
    not count the same ways as get(). However, cache2k has no dedicated counters for get(), but counts every access, which also is a
    hit for the eviction algorithm. This means, the contains() call does not the prevent the entry from beeing evicted, which may
    be undisired. Treating the contains as an access to an entry has pros and cons.
  * JMX statistics: initial getHitRate() value is 0.0
  * JMX statistics: initial getMillisPerFetch() value is 0.0


## API Changes and new methods

  * deprecated: CacheManager.isDestroyed(), replaced by isClosed()

  * new: Cache.peekAndReplace()
  * new: Cache.peekAndRemove()
  * new: Cache.peekAndPut()
  * new: Cache.remove(key, oldValue)
  * new: Cache.replace(key, oldValue, newValue)
  * new: Cache.replace(key, newValue)
  * new: Cache.peekAll()
  * new: Cache.isClosed()
  * new: CacheManager.isClosed()
  * new: Cache.getCacheManager()
  * new: Cache.removeAll()
  * new: Cache.invoke() and Cache.invokeAll()
  * new: Cache.putAll()

  * Stronger typing: Added generic types to the methods CacheBuilder.entryExpiryCalculator and CacheBuilder.exceptionExpiryCalculator
