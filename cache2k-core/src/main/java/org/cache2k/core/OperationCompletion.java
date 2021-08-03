package org.cache2k.core;

/*
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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

import org.cache2k.io.CacheLoaderException;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Completes when each operation for a key completes and propagates exception.
 * At the moment the keys are not used and just a simple count is done. Later we
 * may do additional bookkeeping and fault detection based on the keys.
 *
 * @author Jens Wilke
 */
public class OperationCompletion<K> {

  private final CompletableFuture<Void> future = new CompletableFuture<>();
  private final int initialCount;
  private volatile int countDown;
  private volatile int exceptionCount;
  private volatile Throwable exception;

  public OperationCompletion(Set<K> keys) {
    initialCount = countDown = keys.size();
  }

  /**
   * Notify that operation for one key has been completed. Calls listeners when
   * all keys of the bulk operation have been processed.
   *
   * @param key key which operation completed. The key is not used internally at the
   *            moment. May be used at a later time for debugging or error reporting
   *            purposes.
   * @param exception exception if completed exceptionally, or null on success
   */
  public void complete(K key, Throwable exception) {
    if (exception != null) {
      BULK_OP_EXCEPTION_COUNT.incrementAndGet(this);
      BULK_OP_EXCEPTION.compareAndSet(this, null, exception);
    }
    int i = BULK_OP_COUNT.decrementAndGet(this);
    if (i == 0) { allCompleted(); }
  }

  public CompletableFuture<Void> getFuture() {
    return future;
  }

  private void allCompleted() {
    if (exceptionCount == 0) {
      future.complete(null);
      return;
    }
    CacheLoaderException bulkLoaderException =
      BulkResultCollector.createBulkLoaderException(exceptionCount, initialCount, exception);
    future.completeExceptionally(bulkLoaderException);
  }

  static final AtomicIntegerFieldUpdater<OperationCompletion> BULK_OP_COUNT =
    AtomicIntegerFieldUpdater.newUpdater(OperationCompletion.class, "countDown");

  static final AtomicIntegerFieldUpdater<OperationCompletion> BULK_OP_EXCEPTION_COUNT =
    AtomicIntegerFieldUpdater.newUpdater(OperationCompletion.class, "exceptionCount");

  static final AtomicReferenceFieldUpdater<OperationCompletion, Throwable> BULK_OP_EXCEPTION =
    AtomicReferenceFieldUpdater.newUpdater(OperationCompletion.class, Throwable.class,
      "exception");

}
