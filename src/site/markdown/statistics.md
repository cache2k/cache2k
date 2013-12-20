# Statistics

For each cache a comprehensive set of statistics is available and can be retrieved via JMX.
A complete list and description of all metrics is within the
[cache2k-jmx-api JavaDocs](cache2k-jmx-api/apidocs/index.html).

## toString() output

Each cache make all data available by toString(). Example:

```
Cache{testCache}(size=600, maxSize=600, usageCnt=26311, missCnt=15830,
  fetchCnt=15830, fetchesInFlightCnt=0, newEntryCnt=15830, bulkGetCnt=0,
  refreshCnt=0, refreshSubmitFailedCnt=0, refreshHitCnt=0, putCnt=0,
  expiredCnt=0, evictedCnt=15230, keyMismatch=0, dataHitRate=39.83%,
  cacheHitRate=39.83%, collisionCnt=110, collisionSlotCnt=105,
  longestCollisionSize=3, hashQuality=76.5%, msecs/fetch=0,
  created=2013-12-20 11:17:30.332, cleared=2013-12-20 11:17:30.332,
  infoCreated=2013-12-20 11:17:31.212, infoCreationDeltaMs=0, impl="ArcCache",
  arcP=216, t1Size=216, t2Size=384, integrityState=0.14.e492b12e)
```

## Enabling JMX support

The JMX support is currently build-in. It may be separated into another jar to lower the footprint
of the cache core in the future. The entries show up in the domain `org.cache2k`.

## Special metrics

### Hash quality

The is a single value between 100 and 0 to help evaluate the quality of the hashing function.
100 means perfect. This metric takes into account the collision to size ratio, the longest collision size
and the collision to slot ratio. The value reads 0 if the longest collision size gets more
then 20.

The number of collisions and the longest size of a collision slot are also available via JMX.

### Key mutation counter

Storing objects by reference means it is possible for the application to alter the object
value after it was involved in a cache operation. In case of the key object this means that the
internal data structure of the cache will be invalid after the illegal mutation.

Within cache2k the cache is able to go on with operation and will remove the entries with the
mutated keys, because they will not be accessed any more. A warning is written to the log
and the metric `keyMutationCnt` is incremented.

When ever the counter is non-zero, please correct your application.

### Health

The cache as well as the cache manager have a single value health status for systems monitoring.
The value 0 means everything is okay. A warning level (1) is signalled, if:

   * key mutation happens
   * hash quality below 30

A failure level (2) is signalled, if:

   * integrity error
   * hash quality below 5

More indicators will be added to the health state in the future. However, the meaning of the health
status will stay at a general level.
