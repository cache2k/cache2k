# cache2k version 1.0-RC1 "Bahia de Santa Cruz"

## New and Noteworthy

A lot of API movement, since we work towards 1.0. See `Potential breakages` and `API changes`.
The API is not completely stable yet, but almost.

- SLF4J support

## Potential breakages

Changes in semantics or API that may break existing applications are listed here. 
Modifications in the statistics output will not listed as breakage.


## Bug fixes

If something is listed here it might affect an existing application and updating is recommended.

- OSGi: Add missing export of package `org.cache2k.configuration`
- make eternal default, there is no need to specify either `eternal` or `expiryAfterWrite` any 
  more explicitly, closes: https://github.com/cache2k/cache2k/issues/21
- Fix size methods (was stack overflow): `Cache.asMap().values().size()`, `Cache.asMap().entrySet().size()`, `Cache.asMap().keySet().size()`, 
  closes: https://github.com/cache2k/cache2k/issues/51
- `Cache.asMap()`: Fix `equals()` implementation, correct optional `null` support

## Fixes and Improvements

 
## API Changes and new methods

