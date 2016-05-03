# cache2k version 0.26-BETA "Madeira Beach"

## New and Noteworthy


## Potential breakages

Changes in semantics or API that may break existing applications are listed here. 
Modifications in the statistics output will not listed as breakage.

  * Classes/interfaces: AnyBuilder, BaseAnyBuilder and RootAnyBuilder removed
  * `ExceptionPropagator` interface changed
  * LoadCompletionListener.loadException now expects a `Throwable`

## Bug fixes

If something is listed here it might affect an existing application and updating is recommended.

## Fixes and Improvements

Fixes of corner cases that are most likely not affecting any existing applications and improvements are listed here.

  * `ExceptionPropagator` gets structured context information about exception
  * Eviction: Don't generate ghost entries when no eviction is needed. Improves performance when cache is operated 
    below the maximum capacity.
  * Add missing expiry event, when entry is expired immediately after an update  
  * EntryProcessor did not honor expiry times or policy
  * Remove the output of the build information to standard error
  * Higher safety gap (27 seconds) for timer event, if sharp expiry is requested
 
## API Changes and new methods

  * Classes/interfaces: AnyBuilder, BaseAnyBuilder and RootAnyBuilder removed; Originally intended for fluent 
    configuration sections in builders, we will do another approach.
  * `Cache.getTotalEntryCount()` is deprecated and will be remove in an upcoming release
  * Return values and parameters related to the cache size converted to long
  * Cleanup JMX info bean contents (unfinished)
 
  
  
