Documentation of design decisions and rationale.

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






