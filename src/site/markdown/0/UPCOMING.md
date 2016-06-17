# cache2k version 0.27-BETA "Fort Lauderdale"

## New and Noteworthy

A lot of API movement, since we work towards 1.0. See `Potential breakages` and `API changes`.
The API is not stable yet.

## Potential breakages

Changes in semantics or API that may break existing applications are listed here. 
Modifications in the statistics output will not listed as breakage.

 - null values are not allowed (any more!) by default. The use of a null value can be enabled again via
   `permitNullValues`. The old deprecated builder (CacheBuilder) configures the cache to
   permit null values by default to be backwards compatible to applications still using the version
   0.20-ish interfaces.

## Bug fixes

If something is listed here it might affect an existing application and updating is recommended.

 
## Fixes and Improvements

 - `getAll()` returns a stable map, not affected by expiry and parallel cache modifications.
 - Fixed possible inconsistency of CacheEntry value and timestamp, when concurrent update happens
 - Tiny eviction implementation cleanup and simplification
 - JCache: Custom classloader used for serialization of keys and values
 - JCache: improved performance when not using custom expiry policy
 - JCache: extended configuration of cache2k features via JCache is possible
 - Exceptions in the expiry policy and resilience policy will be propagated as `CacheLoaderException` 
   if happened during load 
 - asMap()
 - history size of Clock-Pro eviction reduced to one half of the capacity
 - getAll() returns empty map if no loader is defined and requested keys are not in cache
 - get() return null if no loader is defined and entry is not in cache (was exception before).
 - `CacheEntry.getValue()` and `MutableCacheEntry.getValue()` throws exception if loader exception happened
 - `Cache.invoke`: Exceptions from entry processor propagated correctly as `EntryProcessingException`
 - Global `ExceptionPropagator` customizable via tunable mechanism.
 - `CacheEntryProcessor` renamed to `EntryProcessor`
 - `CacheEntryProcessingException` renamed to `EntryProcessingException`
 - `Cache.invoke` and `Cache.invokeAll`: Remove var arg argument for arbitrary objects. 
   Better aligned to Java 8 lambdas.
 - `Cache2kBuilder.keepDataAfterExpired`: has become false by default
 
## API Changes and new methods

 - `contains()` replaced with `containsKey()`
 - `expire()` expires an entry manually or resets the expiry time
 - Rename:`CacheConfiguration` to `Cache2kConfiguration`
 - Removed `clearTimingStatistics` operation from JMX
 - `Cache.iterator()` is deprecated, alternative is `Cache.entries()` or `Cache.keys()` 
