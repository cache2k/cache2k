# cache2k version 0.25-BETA "Indian Shores"

## New and Noteworthy

New builder class `Cache2kBuilder`. A cache is now build the following way:

````
     Cache<Long, List<String>> c =
       new Cache2kBuilder<Long, List<String>>() {}
         .name("myCache")
         .eternal(true)
         .build();
````

The new pattern allows to capture the complete type information. For compatibility reasons the
old builder class `CacheBuilder` is still available to allow applications to transition to the
new builder.

## Potential breakages

Changes in semantics or API that may break existing applications are listed here. In general, only very minor
changes are done with breaking existing semantics, which will most likely not affect existing applications.
Everything that will most likely break applications will be introduced as a new API and the old one will 
get deprecated. Modifications in the statistics output will not listed as breakage.

  * Either expiryDuration or eternal must be set explicitly in this release. See: https://github.com/cache2k/cache2k/issues/21
  * Return value of `Cache.iterator()` changed from `ClosableIterator` to `Iterator`
  * `ExperimentalBulkCacheSource` removed
  * CacheConfig renamed and moved to new destination

## Bug fixes

If something is listed here it might affect an existing application and updating is recommended.


## Fixes and Improvements

Fixes of corner cases that are most likely not affecting any existing applications and improvements are listed here.

  * JavaDoc improvements (ongoing...)
  * `CacheManager.getCache()` does not return a closed or uninitialized cache
  * Fix a potential race condition: timer event / entry update
  * Make the iterator more robust. Problems with the previous solution: The iterator could block the cache 
    if the iteration is not  finished completely or the iterator was not closed. In case of concurrent updates to 
    the cache the iterator could continue iterating as long as entries are inserted.

## API Changes and new methods

  * Return value of `Cache.iterator()` changed from `ClosableIterator` to `Iterator`
  * Cache2kBuilder as replacement for CacheBuilder
  * Option keepDataAfterExpired renamed to keepValueAfterExpired



