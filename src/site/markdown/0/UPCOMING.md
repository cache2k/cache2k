# cache2k version 0.27-BETA "Fort Lauderdale"

## New and Noteworthy

A lot of API movement, since we work towards 1.0. See `Potential breakages` and `API changes`.
The API is not stable yet.

## Potential breakages

Changes in semantics or API that may break existing applications are listed here. 
Modifications in the statistics output will not listed as breakage.

## Bug fixes

If something is listed here it might affect an existing application and updating is recommended.

 
## Fixes and Improvements

 - `getAll()` returns a stable map, not affected by expiry and parallel cache modifications.
 - Fixed possible inconsistency of CacheEntry value and timestamp, when concurrent update happens
 - Tiny eviction implementation cleanup and simplification
 
## API Changes and new methods

 - `contains()` replaced with `containsKey()`
 - `expire()` expires an entry manually or resets the expiry time
 - Rename:`CacheConfiguration` to `Cache2kConfiguration`
 - Removed `clearTimingStatistics` operation from JMX
