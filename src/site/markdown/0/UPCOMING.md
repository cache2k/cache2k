# cache2k version 0.25-BETA "Indian Shores"

## New and Noteworthy


## Potential breakages

Changes in semantics or API that may break existing applications are listed here. In general, only very minor
changes are done with breaking existing semantics, which will most likely not affect existing applications.
Everything that will most likely break applications will be introduced as a new API and the old one will 
get deprecated. Modifications in the statistics output will not listed as breakage.

  * Either expiryDuration or eternal must be set explicitly in this release. See: https://github.com/cache2k/cache2k/issues/21

## Bug fixes

If something is listed here it might affect an existing application and updating is recommended.


## Fixes and Improvements

Fixes of corner cases that are most likely not affecting any existing applications and improvements are listed here.

  * JavaDoc improvements (ongoing...)
  * `CacheManager.getCache()` does not return a closed or uninitialized cache
  * Fix a potential race condition: timer event / entry update
  

## API Changes and new methods



