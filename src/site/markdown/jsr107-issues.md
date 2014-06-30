# jsr107 issues

Summary of detected JSR107 and areas of cautions that may arise problems in interoperability as
 well as efficient implementations of the standard.
  
## Cache.iterator()

The standard does not define any guarantees on concurrency or minimum guarantees at all, 
see: https://github.com/jsr107/jsr107spec/issues/130. If a legal implementation may
 choose to fail fast, it should be specified. There should always a minimum guarantee to
 applications make the operation usable at all.

Special operations that may interact with iterations are close() and clear().

Iteration on a cache is a heavy operation, has some resource consumption and may
include I/O operations with prefetching and buffering. If the iterator is not read 
to the end, there needs to be a mechanism to free any resources.
 

 

 
 