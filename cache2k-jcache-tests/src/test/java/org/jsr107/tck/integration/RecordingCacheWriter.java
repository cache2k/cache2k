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
import javax.cache.integration.CacheWriter;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A CacheWriter implementation that records the entries written and deleted from it so
 * that they may be later asserted.
 *
 * @param <K> the type of the keys
 * @param <V> the type of the values
 */
public class RecordingCacheWriter<K, V> implements CacheWriter<K, V>, AutoCloseable {

    /**
     * A writtenKeys of keys to values that have been written.
     */
    private ConcurrentHashMap<K, V> writtenKeys;


    /**
     * A writtenKeys of keys to values that have been deleted.
     */
    private ConcurrentHashMap<K, V> deletedEntries;

    /**
     * The number of writes that have so far occurred.
     */
    private AtomicLong writeCount;

    /**
     * The number of deletes that have so far occurred.
     */
    private AtomicLong deleteCount;

    /**
     * Constructs a RecordingCacheWriter.
     */
    public RecordingCacheWriter() {
        this.writtenKeys = new ConcurrentHashMap<>();
        this.deletedEntries = new ConcurrentHashMap<>();
        this.writeCount = new AtomicLong();
        this.deleteCount = new AtomicLong();
    }

    @Override
    public void write(Cache.Entry<? extends K, ? extends V> entry) {
        writtenKeys.put(entry.getKey(), entry.getValue());
        writeCount.incrementAndGet();
    }

    @Override
    public void writeAll(Collection<Cache.Entry<? extends K, ? extends V>> entries) {
        Iterator<Cache.Entry<? extends K, ? extends V>> iterator = entries.iterator();
        while (iterator.hasNext()) {
            write(iterator.next());
            iterator.remove();
        }
    }

    @Override
    public void delete(Object key) {
        V value = writtenKeys.remove((K)key);
        if (value != null) {
            deletedEntries.put((K) key, value);
        }
        deleteCount.incrementAndGet();
    }

    @Override
    public void deleteAll(Collection<?> entries) {
        for (Iterator<?> keys = entries.iterator(); keys.hasNext(); ) {
            delete(keys.next());
            keys.remove();
        }
    }

    /**
     * Gets the last written value of the specified key
     *
     * @param key the key
     * @return the value last written
     */
    public V get(K key) {
        return writtenKeys.get(key);
    }

    /**
     * Determines if there is a last written value for the specified key
     *
     * @param key the key
     * @return true if there is a last written value
     */
    public boolean hasWritten(K key) {
        return writtenKeys.containsKey(key);
    }

    /**
     * Determines if this key was last deleted
     *
     * @param key the key
     * @return true if there is a last written value
     */
    public boolean hasDeleted(K key) {
        return deletedEntries.containsKey(key);
    }

    /**
     * Gets the number of writes that have occurred.
     *
     * @return the number of writes
     */
    public long getWriteCount() {
        return writeCount.get();
    }

    /**
     * Gets the number of deletes that have occurred.
     *
     * @return the number of writes
     */
    public long getDeleteCount() {
        return deleteCount.get();
    }

    /**
     * Clears the contents of stored values.
     */
    public void clear() {
        writtenKeys.clear();
        deletedEntries.clear();
        this.writeCount = new AtomicLong();
        this.deleteCount = new AtomicLong();
    }

  @Override
  public void close() throws Exception {
    // added for code coverage.
  }
}
