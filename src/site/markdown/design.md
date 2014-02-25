Documentation of design decisions and rationale.

## Clean API in separate module

The API is provided in a separate module. This way a clean API is defined and
breakages will be avoided.

## Statistics are not optional, but build in

The cache2k implementations have a fixed set of build-in statistics. There is
no way to switch these on or off. The reasons:

* SysOps always want to have statistics but don't bother about the price of them.
  The conclusion: a cache always needs statistics and these need to be low overhead.
* The cache itself needs statistics for eviction and tuning.
* Statistics are needed for verification of the algorithms.

Right now there is no considerable runtime overhead for statistics, but
statistics need some additional memory. If no statistics are needed and
retrieved the memory per cache for statistics is about 200 bytes.

The cache also has a statistic on the average time to fetch data from the cache source.
We need on timer call anyways, when the data is requested, so a second one when it
finally is available is not much additional overhead.

## Less options

There are a lot of caches within our application. It is not possible to decide
for each cache about the size or the best eviction algorithm. The design of
cache2k tries to have as less options as possible that need to be set.

## New features may not cost performance

All new or extra features are not allowed to slow down the cache hit case.

## Keep the core small

The core cache functionality should come in a tiny jar, so it is not much
hassle and overhead to include it.

## peek() and get()

Cache.get() returns a value from the cache, or retrieves it from the cache source.
The method Cache.peek() just returns a value from the cache, or null if no
data was available. In a JSR107 cache implementation there is a single method for both. If no data source
is present, Cache.get() returns null for an unmapped value.

Since the caller intentions are different as well as different semantics are
needed it is better to split the two methods. The code inside the cache
is cleaner because the presence of the cache source does not need to be checked
to alter the semantics. Furthermore the behaviour of peek() may be useful if
a cache source is present.

Right now there is a getAll() but no corresponding peekAll(), since the bulk
operations only make sense in read-through operation.

## Bulk API and JSR107 getAll()

After implementing the getAll() JSR107 on top of C2K I came to the conclusion that
it has to die again. Its still in the implementation but not exposed by the API.










