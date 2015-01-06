package org.cache2k.storage;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2015 headissue GmbH, Munich
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

import java.io.IOException;
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
  public StorageEntry get(Object key) throws Exception;

  /**
   * Stores the entry in the storage. The entry instance is solely for transferring
   * the data, no reference may be hold within the storage to it. The callee takes
   * care that the entry data is consistent during the put method call.
   *
   * <p/>If a put operation fails an implementation should try to remove an
   * existing entry bound to the key and then throw the exception.
   *
   * @throws IOException may be thrown if hope is lost
   */
  public void put(StorageEntry e) throws Exception;

  public boolean remove(Object key) throws Exception;

  /**
   * Returns true if there is a mapping for the key.
   *
   * <p>An exception on contains is not severe. The cache will try other sources or
   * return null.
   */
  public boolean contains(Object key) throws Exception;

  /**
   * Remove all entries from the cache and free resources. This operation is called
   * when there is no other operation concurrently executed on this storage instance.
   *
   * <p/>When a Cache.clear() is initiated there is no obligation to send a
   * CacheStorage.clear() to the persisted storage. Alternatively, all objects can
   * be removed via remove().
   */
  public void clear() throws Exception;

  /**
   * Free all resources and stop operations immediately.
   */
  public void close() throws Exception;

  /**
   * Iterate over all stored entries and call the entry visitor. It is generally safe to
   * return expired entries. If {@link org.cache2k.storage.PurgeableStorage} is not
   * implemented, returning expired entries is a must, to support the generic purge
   * algorithm.
   *
   * <p/>If the {@link ExecutorService} is used, the method may return immediately without
   * the waiting for all threads to finish. This is done by the caller, when needed.
   */
  public void visit(VisitContext ctx, EntryFilter f, EntryVisitor v) throws Exception;

  public int getEntryCount();

  public static interface MultiThreadedContext {

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
     * {@link java.util.concurrent.ExecutorService#invokeAll(java.util.Collection, long, java.util.concurrent.TimeUnit)},
     * {@link java.util.concurrent.ExecutorService#invokeAny(java.util.Collection)},
     * {@link java.util.concurrent.ExecutorService#invokeAny(java.util.Collection, long, java.util.concurrent.TimeUnit)}
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

     boolean shouldInclude(Object _key);

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
