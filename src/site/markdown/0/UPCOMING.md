# cache2k version 0.27-BETA "Fort Lauderdale"

## New and Noteworthy

A lot of API movement, since we work towards 1.0. See `Potential breakages` and `API changes`.
The API is not completely stable yet, but almost.

- Use of Java 8 features (StampedLock), when available, to improve performance of cache mutations
- Eviction allows segmentation, to improve multi core performance of insert and remove operations 
  on the cache (`Cache2kBuilder.evictionSegmentCount`)
- Various other performance improvements
- Lots of work on the documentation. Not yet published. See 
  [the GitHub source](https://github.com/cache2k/cache2k/tree/master/doc/src/docs/asciidoc/user-guide/sections)

## Potential breakages

Changes in semantics or API that may break existing applications are listed here. 
Modifications in the statistics output will not listed as breakage.

 - null values are not allowed (any more!) by default. The use of a null value can be enabled again via
   `permitNullValues`. The old deprecated builder (CacheBuilder) configures the cache to
   permit null values by default to be backwards compatible to applications still using the version
   0.20-ish interfaces.
 - LoadCompletedListener renamed to CacheOperationCompletionListener
 - `Cache.load` and `Cache.reload`: Signature change, swap the two parameters
 - `Cache.invoke` and `Cache.invokeAll`: Remove var arg argument for arbitrary objects. 
   Better aligned to Java 8 lambdas.

## Bug fixes

If something is listed here it might affect an existing application and updating is recommended.

## Fixes and Improvements

 - `getAll()` returns a stable map, not affected by expiry and parallel cache modifications.
 - Fixed possible inconsistency between `CacheEntry.getValue()` and `CacheEntry.getLastModification()`, when concurrent update happens
 - Eviction implementation cleanup and simplification
 - JCache: Custom classloader used for serialization of keys and values
 - JCache: improved performance when not using custom expiry policy
 - JCache: extended configuration of cache2k features via JCache is possible
 - Exceptions in the expiry policy and resilience policy will be propagated as `CacheLoaderException` 
   if happened during load 
 - asMap() provided `ConcurrentMap` interface view of the cache
 - Reduce memory footprint: history size of Clock-Pro eviction reduced to one half of the capacity
 - `getAll()` returns empty map if no loader is defined and requested keys are not in cache
 - `get()` return `null` if no loader is defined and entry is not in cache (was exception before).
 - `CacheEntry.getValue()` and `MutableCacheEntry.getValue()` throws exception if loader exception happened
 - `Cache.invoke`: Exceptions from entry processor propagated correctly as `EntryProcessingException`
 - Global `ExceptionPropagator` customizable via tunable mechanism.
 - `CacheEntryProcessor` renamed to `EntryProcessor`
 - `CacheEntryProcessingException` renamed to `EntryProcessingException`
 - `Cache2kBuilder.keepDataAfterExpired`: has become false by default
 - `EntryProcessor`: Triggers load when `MutableEntry.getException` is called
 - `EntryProcessor`: `MutableEntry.setException` or `MutableEntry.setExpiry` work correctly after loading the value
 - Statistics: Correct usage counter in case of a race at entry mutation 
 - `new Cache2kBuilder(){}`, yields proper exception instead of just `ClassCastException`
 - `new Cache2kBuilder<Object, Object>(){}`, yields `IllegalArgumentException`, use `Cache2kBuilder.forUnkownTypes()`
 - lots of internal cleanup
 - Interface `ExpiryTimeValues` with constants for special expiry times
 
## API Changes and new methods

 - `contains()` replaced with `containsKey()`
 - `expire()` expires an entry manually or resets the expiry time
 - Rename:`CacheConfiguration` to `Cache2kConfiguration`
 - Removed `clearTimingStatistics` operation from JMX
 - `Cache.iterator()` is deprecated, alternative is `Cache.entries()` or `Cache.keys()` 
 - `Cache.invoke` and `Cache.invokeAll`: Remove var arg argument for arbitrary objects. 
   Better aligned to Java 8 lambdas.
