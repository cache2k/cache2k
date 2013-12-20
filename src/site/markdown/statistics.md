# Statistics

For each cache a comprehensive set of statistics is available and can be retrieved via JMX.
It is not possible to switch statistic gathering on or off, statistics are always available.
Right now the statistics are exposed via JMX always. Maybe this gets an optional part.

 A complete list and description of all metrics is within the
[/cache2k-jmx-api/apidocs/index.html](cache2k-jmx-api JavaDocs).

## toString() output

## JMX support


## Special metrics

### Hash quality

The is a single value between 100 and 0 to help evaluate the quality of the hashing function.
100 means perfect. This metric takes into account the collision to size ratio, the longest collision size
and the collision to slot ratio. The value reads 0 if the longest collision size gets more
then 20.

The number of collisions and the longest size of a collision slot are also available via JMX.

### The key mutation counter

Storing objects by reference means it is possible for the application to alter the object
value after it was involved in a cache operation. In case of the key object this means that the
internal data structure of the cache will be invalid after the illegal mutation.

Within cache2k the cache is able to go on with operation and will remove the entries with the
mutated keys, because they will not be accessed any more. A warning is written to the log
and the metric `keyMutationCnt` is incremented.

When ever the counter is non-zero, please correct your application.






