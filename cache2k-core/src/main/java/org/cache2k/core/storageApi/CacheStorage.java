package org.cache2k.core.storageApi;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.concurrent.ExecutorService;

/**
 * An interface for persistent or off heap storage for a cache.
 *
 * @author Jens Wilke; created: 2014-04-03
 */
public interface CacheStorage {

  /**
   * Retrieve the entry from the storage. If there is no mapping for the
   * key, null is returned.
   *
   * <p>Depending on the cache configuration,  an exception on get is not severe.
   * The cache will try other sources or return null.
   */
  StorageEntry get(Object key) throws Exception;

  /**
   * Stores the entry in the storage. The entry instance is solely for transferring
   * the data, no reference may be hold within the storage to it. The callee takes
   * care that the entry data is consistent during the put method call.
   *
   * <p>If a put operation fails an implementation should try to remove an
   * existing entry bound to the key and then throw the exception.
   *
   * @throws java.io.IOException may be thrown if hope is lost
   */
  void put(StorageEntry e) throws Exception;

  boolean remove(Object key) throws Exception;

  /**
   * Returns true if there is a mapping for the key.
   *
   * <p>An exception on contains is not severe. The cache will try other sources or
   * return null.
   */
  boolean contains(Object key) throws Exception;

  /**
   * Remove all entries from the cache and free resources. This operation is called
   * when there is no other operation concurrently executed on this storage instance.
   *
   * <p>When a Cache.clear() is initiated there is no obligation to send a
   * CacheStorage.clear() to the persisted storage. Alternatively, all objects can
   * be removed via remove().
   */
  void clear() throws Exception;

  /**
   * Free all resources and stop operations immediately.
   */
  void close() throws Exception;

  /**
   * Iterate over all stored entries and call the entry visitor. It is generally safe to
   * return expired entries. If {@link PurgeableStorage} is not
   * implemented, returning expired entries is a must, to support the generic purge
   * algorithm.
   *
   * <p>If the {@link ExecutorService} is used, the method may return immediately without
   * the waiting for all threads to finish. This is done by the caller, when needed.
   */
  void visit(VisitContext ctx, EntryFilter f, EntryVisitor v) throws Exception;

  int getEntryCount();

  interface MultiThreadedContext {

    /**
     * A private executor service for this operation to run in multiple threads.
     * The use of {@link ExecutorService#awaitTermination(long, java.util.concurrent.TimeUnit)} ()}
     * waits only for threads started within the visit operation. Multiple calls to
     * this method return the identical instance.
     *
     * <p>When using {@link java.util.concurrent.Callable} a thrown exception in within the
     * task leads to an abort of the operation, see {@link #abortOnException(Throwable)}.
     *
     * <p>The methods
     * {@link java.util.concurrent.ExecutorService#invokeAll(java.util.Collection,
     * long, java.util.concurrent.TimeUnit)},
     * {@link java.util.concurrent.ExecutorService#invokeAny(java.util.Collection)},
     * {@link java.util.concurrent.ExecutorService#invokeAny(java.util.Collection,
     * long, java.util.concurrent.TimeUnit)}
     * are or may not be supported by the provided implementation.
     */
    ExecutorService getExecutorService();

    /**
     * If threads are started by using {@link #getExecutorService()} waits for termination
     * or tries to stop threads immediately if {@link #shouldStop()} is true. This is also done
     * automatically when the visit method exists.
     */
    void awaitTermination() throws InterruptedException;

    /**
     * True if the operation should stop immediately. Used e.g. during
     * application shutdown.
     */
    boolean shouldStop();

    /**
     * If an exception cannot be handled, this method aborts the operation and
     * propagates the exception to the operation client. Multiple exceptions can
     * occur, since the operation is multi thread. Only the first is propagated.
     * After this method is called {@link #shouldStop()} is true.
     */
    void abortOnException(Throwable ex);

  }

  interface EntryFilter {

     boolean shouldInclude(Object key);

  }

  interface VisitContext extends MultiThreadedContext {

    /**
     * True if entries should have metadata. If false, only the key will be set.
     * Storage implementations may ignore this and always send the metadata.
     */
    boolean needMetaData();

    /**
     * True if entries should have the value field set with the storage contents.
     * Storage implementations may ignore this and always send the metadata.
     */
    boolean needValue();

  }

  interface EntryVisitor {

    void visit(StorageEntry e) throws Exception;

  }

}
