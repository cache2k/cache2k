h# External (out of heap) data storage

## Introduction

Within cache2k the concept _storage_ means that cached data is stored outside
the core heap data structures. This includes persistence, off heap storage, or
remote/network storage. All storage variants, share common properties and
therefore a common part of their configuration.

A cache can have more than one storage configured.

## Error handling

A storage implementation may propagate exceptions to the cache, in the case,
that nothing useful can be done. Depending on the usage scenario or the data
that is stored within the storage, it may be a serious problem or no problem
if a storage operation fails. Thus, the error handling can be tweaked to fit
 the scenario.
 
According with the cache properties a get operation may return data or not, so if
 a storage operations fails, there is the option to ignore this, since there
 is no guarantee that the cache stored the data, or to propagate this.
 
The situation is different for a put or a remove operation. If the operation
fails and the storage is returning the old data, the cache properties are not
met.

### Default handling mode

The idea of the default handling is that it goes with the cache paradigm
"there are no guarantees". A failed operation which can be mitigated 
is logged but not propagated. The error count is incremented and 
the JMX alert goes to orange level.


A storage exception is considered fatal, always. If something fails,
the storage will be switched off, which means, that the cache runs
only with the heap if no other storage is available.

Switching off the storage has two reasons: The data of the storage is
not touched any more, so the situation can be investigated by the operator,
if needed. Second, the application may run within the heap cache safely,
 whereas with continuous storage exceptions the application performance
 properties may change dramatically.

### Unreliable handling mode

Can be used if the storage implementation does not work reliable, e.g. if
a network connection is needed. A failed operation is logged on debug level
 but not propagated.

A storage exception is considered non-fatal if the operation was not critical.

### Reliable handling mode

Used when critical data is stored. Errors are propagated and JMX alert goes
to red level. When reliable handling is expected, the application will gets the
exceptions so it may or will stop operating at once.

### Overview per cache operation

FIXME: do we keep the three modes?

| Storage operation | Default mode    | Unreliable mode   | Reliable mode              |
  ----------------- | --------------- | ----------------- | -------------------------- |
| open              | log and disable | log and disable   | log, disable and propagate |
| put               | mitigate, count and ignore or disable | count and ignore  | log, disable and propagate |
| get               | count and ignore | count and ignore  | log, disable and propagate |
| remove            | count and ignore | count and ignore  | log, disable and propagate |
| contains          | count and ignore | count and ignore  | log, disable and propagate |
| get               | count and ignore | count and ignore  | log, disable and propagate |
| flush             | log and disable | log and disable   | log, disable and propagate |
| expire            | log and disable | count and ignore  | log, disable and propagate |
| iterate/visit     | log and disable | count and ignore  | log, disable and propagate |

- *log and disable*:  

## Monitoring

General counters?


A cache clear operation will return immediately and not hold up any other operations while the
storage implementation may need some time to cleanup, e.g. if 1TB of files need to be
 removed. During this time storage operations are buffered and sent to the storage
 when it is back online. The cache alert level switches to orange when the storage
 is performing the clear. This way we have alerting when the clear is taking to 
 long or does not finish at all.

## Logging


