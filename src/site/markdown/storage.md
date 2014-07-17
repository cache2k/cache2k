# External (out of heap) data storage

## Introduction

Within cache2k the concept _storage_ means that cached data is stored outside
the core heap data structures. This includes persistence, off heap storage, or
remote/network storage. All storage variants, share common properties and
therefore a common part of their configuration.

A cache can have more than one storage configured.

## Features

### Offline clear()

Caches may be cleared during operation. A storage need some time to proceed
with the clear operation, e.g. when 1TB of files need to be deleted.
A cache.clear() initiates a storage clear, but continues operation and
buffers all storage requests, until the storage is ready.

### Storage aggregation



## Error handling

A storage implementations may throw exceptions and leave it to the
general error handling mechanism to do something useful. Depending on
 the situation the exception will be propagated to the application or
 not.

### Default handling mode

The idea of the default handling is that it goes with the cache paradigm. 
A read operation may return an entry or not, even when an entry was 
stored before. In the event of an exception the problem just gets counted 
and logged and no entry will be returned.

Cache mutations are more difficult. After a successful cache put the new entry
  mut be returned or no entry. Returning the previous entry would be a failure.

### Reliable handling mode

This mode can be used when critical data is stored. If a mutation is not 
successful an exceptions is propagated to the application.

### Overview per cache operation

| Storage operation | Default mode                                |  Reliable mode    |
  ----------------- | ------------------------------------------- | ----------------- |
| open              | disable and ignore                          | disable and propagate, cache unusable |
| put               | mitigate and ignore / disable and propagate | disable and propagate, cache unusable |
| get               | ignore                                      | disable and propagate, cache unusable |
| remove            | disable and propagate                       | disable and propagate, cache unusable |
| contains          | ignore                                      | disable and propagate, cache unusable |
| flush             | disable and ignore                          | disable and propagate, cache unusable |
| expire            | disable and ignore                          | disable and propagate, cache unusable |
| iterate/visit     | disable and ignore                          | disable and propagate, cache unusable |

- *disable and ignore*: Storage module is disabled, cache continues operation without storage interaction
- *disable and propagate*: Storage module disabled, exceptions will be propagated to the application
- *cache unusable*: Complete cache is unusable and will throw exceptions
- *mitigate and ignore*: If put() fails, try to remove the entry, if this works ignore the problem
- *ignore*: Just count the error. get() returns null

## Monitoring

General counters?

A cache clear operation will return immediately and not hold up any other operations while the
storage implementation may need some time to cleanup, e.g. if 1TB of files need to be
 removed. During this time storage operations are buffered and sent to the storage
 when it is back online. The cache alert level switches to orange when the storage
 is performing the clear. This way we have alerting when the clear is taking to 
 long or does not finish at all.

## Logging


## 