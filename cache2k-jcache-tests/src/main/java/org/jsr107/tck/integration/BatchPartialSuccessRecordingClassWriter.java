/**
 *  Copyright 2011-2013 Terracotta, Inc.
 *  Copyright 2011-2013 Oracle, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */



package org.jsr107.tck.integration;

import javax.cache.Cache;
import javax.cache.CacheException;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulate Partial Success in batch operations.
 * Cache mutation for an entry occurs only when write-through succeeds.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class BatchPartialSuccessRecordingClassWriter<K, V> extends RecordingCacheWriter<K, V> {

    /**
     * simulate delete failure at this rate. default is fail every third delete operation.
     */
    private int simulatedDeleteFailure = 3;

    /**
     * simulate write failure at this rate. default is fail every third write operation.
     */
    private int simulatedWriteFailure = 3;

    private AtomicInteger numWrite = new AtomicInteger(1);
    private AtomicInteger numDelete = new AtomicInteger(1);

    public BatchPartialSuccessRecordingClassWriter(int simulatedWriteFailure, int simulatedDeleteFailure) {
        this.simulatedWriteFailure = simulatedWriteFailure;
        this.simulatedDeleteFailure = simulatedDeleteFailure;
    }

    /**
     * Some implementations may not call writeAll.  So this method fails every {@link #simulatedWriteFailure} times.
     * @param entry to write
     * @throws CacheException to simulate partial failure of write-through
     */
    public void write(Cache.Entry<? extends K, ? extends V> entry) {
        if ((numWrite.getAndIncrement() % simulatedWriteFailure) == 0) {
            throw new CacheException("simulated failure of write entry=[" + entry.getKey() + "," + entry.getValue()
                                     + "]");
        } else {
            super.write(entry);
        }
    }

    /**
     * Some implementations may not call deleteAll.  So this method fails every {@link #simulatedDeleteFailure} times.
     * @param key to delete
     * @throws CacheException to simulate partial failure of write-through
     */
    public void delete(Object key) {
        if ((numDelete.getAndIncrement() % simulatedDeleteFailure) == 0) {
          throw new CacheException("simulated failure of delete(" + key + ")");
        } else {
            super.delete(key);
        }
    }

    /**
     * Always partial success.
     *
     * Randomly simulate a write failure for one of the entries.
     * @param entries to write and upon return the entries that have not been written.
     * @throws CacheException to simulate partial success.
     */
    @Override
    public void writeAll(Collection<Cache.Entry<? extends K, ? extends V>> entries) {
        Iterator<Cache.Entry<? extends K, ? extends V>> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Cache.Entry<? extends K, ? extends V> entry = iterator.next();
            if ((numWrite.getAndIncrement() % simulatedWriteFailure) == 0) {
                throw new CacheException("simulated write failure for entry " + entry.getKey() + ","
                                             + entry.getValue());
            } else {
                super.write(entry);
                iterator.remove();
            }
        }
    }

    /**
     * Always partial success.
     *
     * Randomly simulate a delete failure for one of the entries.
     * @param entries to delete
     * @throws CacheException to simulate partial success.
     */
    @Override
    public void deleteAll(Collection<?> entries) {

        for (Iterator<?> keys = entries.iterator(); keys.hasNext(); ) {
            Object key = keys.next();
            if ((numDelete.getAndIncrement() % simulatedDeleteFailure) == 0) {
              throw new CacheException("simulated delete failure for key " + key);
            } else {
                super.delete(key);
                keys.remove();
            }
        }
    }
}
