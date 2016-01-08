package org.cache2k.core.test;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import org.cache2k.Cache;
import org.cache2k.CacheBuilder;
import org.cache2k.StorageConfiguration;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Examples with writing data to the file system and using the additional configuration options.
 *
 * <p><b>Warning:</b> The persistence/storage support is ALPHA quality. The functionality and API
 * needs to be finalized, and more thorough testing need to be done under heavy load with high
 * concurrency.
 *
 * <p>In the domain of cache2k the term <em>storage</em> is used for any external
 * connected storage mechanism, including off-heap or connection to remote systems, file systems
 * and databases. The term <em>persistent</em> is used for durable storage only like
 * file systems and databases.
 *
 * @author Jens Wilke
 */
@Ignore("not working")
public class PersistenceExampleTest {

  /**
   * Persistence is switched on by specifying {@link CacheBuilder#persistence}.
   * The parameter flushOnClose(true) ensures that all data is written in the close system call.
   *
   * <p>The current persistence implementation stores the data in a file named
   * <code>cache2k-storage:$MANAGERNAME:$CACHENAME.img</code>. Additional files containing
   * index and meta information will stored with the same prefix and <code>-$NUMBER.dsc</code>
   * and <code>-$NUMBER.idx</code>. The final name, auto generated file name in this example
   * is: <code>cache2k-storage:default:persistent-cache.img</code>. The file name used can be
   * changed with the parameter {@link org.cache2k.StorageConfiguration.Builder#storageName(String)}.
   * The directory can be changed with the parameter
   * {@link org.cache2k.StorageConfiguration.Builder#location(String)}.
   *
   * <p>Stored object need to implement {@link java.io.Serializable}. The support for
   * different marshalling mechanisms needs to be finished/documented.
   *
   * <p>In the current file system storage, calling {@link Cache#clear} before closing,
   * deletes the used files completely.
   *
   * <p>By concept, {@link org.cache2k.RootAnyBuilder#persistence} selects a storage configuration
   * with a reasonable default for durable data storage.
   */
  @Test
  public void putClosePeek() {
    Cache<String, String> c =
      CacheBuilder.newCache(String.class, String.class)
        .name("persistent-cache")
        .persistence()
          .location(System.getProperty("java.io.tmpdir"))
          .flushOnClose(true)
        .build();
    c.put("a", "Hello");
    c.close();
    c = CacheBuilder.newCache(String.class, String.class)
        .name("persistent-cache")
        .persistence()
          .location(System.getProperty("java.io.tmpdir"))
          .flushOnClose(true)
        .build();
    assertEquals("Hello", c.peek("a"));
    c.clear();
    c.close();
  }

  /**
   * To ensure the data is flushed to disk, applications can also call a explicit flush
   * method. This is needed for the purposes of this test case to check whether the data
   * was written. An explicit flush not needed in the general case. The current implementation
   * will flush the data after {@value org.cache2k.StorageConfiguration#DEFAULT_FLUSH_INTERVAL_SECONDS}
   * seconds by default. This can be configured by
   * {@link org.cache2k.StorageConfiguration.Builder#flushInterval(long, TimeUnit)}
   */
  @Test
  public void explicitFlush() {
    Cache<String, String> c =
      CacheBuilder.newCache(String.class, String.class)
        .name("persistent-cache-flush")
        .persistence()
        .build();
    c.put("a", "Hello");
    c.flush();
    c.close();
    c = CacheBuilder.newCache(String.class, String.class)
      .name("persistent-cache-flush")
      .persistence()
      .build();
    assertEquals("Hello", c.peek("a"));
    c.clear();
    c.close();
  }

  /**
   * This should test the automatic flush after
   * {@value org.cache2k.StorageConfiguration#DEFAULT_FLUSH_INTERVAL_SECONDS}
   * seconds.
   */
  @Test
  public void autoFlush() throws Exception {
    Cache<String, String> c =
      CacheBuilder.newCache(String.class, String.class)
        .name("persistent-cache-autoflush")
        .persistence()
        .build();
    c.put("a", "Hello");
    Thread.sleep(StorageConfiguration.DEFAULT_FLUSH_INTERVAL_SECONDS * 1000 + 3000);
    c.close();
    c = CacheBuilder.newCache(String.class, String.class)
      .name("persistent-cache-autoflush")
      .persistence()
      .build();
    assertEquals("Hello", c.peek("a"));
    c.clear();
    c.close();
  }

  /**
   * Example configuration that keeps I/O at a minimum, small heap capacity and
   * a big storage capacity. Entries start living in the heap and overflow to the
   * storage.
   *
   * <p>
   *   <ul>
   *     <li><b><code>maxSize(...)</code></b>: Sets the total capacity.</li>
   *     <li><b><code>heapEntryCapacity(...)</code></b>: Sets the capacity of the heap.</li>
   *     <li><b><code>passivation(true)</code></b>: Entries will only moved to storage only when evicted from
   *     the heap, or, on shutdown.</li>
   *   </ul>
   * </p>
   *
   * <p>
   * FIXME: This needs thorough testing. Check dirty tagging.
   * FIXME: Overflow/eviction from the heap should be done async in separate thread.
   */
  @Test
  public void overflowCache() {
    Cache<String, String> c =
      CacheBuilder.newCache(String.class, String.class)
        .name("overflow")
        .maxSize(10 * 1000 * 1000)
        .heapEntryCapacity(100 * 1000)
        .persistence()
          .flushInterval(27, TimeUnit.SECONDS)
          .passivation(true)
          .flushOnClose(true)
        .build();
    c.close();
  }

}
