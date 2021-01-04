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

import org.cache2k.CacheException;
import org.cache2k.CacheOperationCompletionListener;
import org.cache2k.io.CacheLoaderException;

import java.util.Set;
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
  final CacheOperationCompletionListener listener;
  int initialCount;
  volatile int countDown;
  volatile int exceptionCount;
  volatile Throwable exception;

  public OperationCompletion(Set<K> keys, CacheOperationCompletionListener l) {
    initialCount = countDown = keys.size();
    this.listener = l;
  }
  public void complete(K key, Throwable t) {
    if (t != null) {
      BULK_OP_EXCEPTION_COUNT.incrementAndGet(this);
      BULK_OP_EXCEPTION.compareAndSet(this, null, t);
    }
    int i = BULK_OP_COUNT.decrementAndGet(this);
    if (i == 0) { allCompleted(); }
  }

  private void allCompleted() {
    if (exceptionCount == 0) {
      listener.onCompleted();
      return;
    }
    String txt = null;
    if (exceptionCount > 1) {
      txt = "finished with " + exceptionCount + " exceptions " +
        "out of " + initialCount + " operations" +
        ", one propagated as cause";
    }
    listener.onException(wrap(txt, exception));
  }

  CacheException wrap(String txt, Throwable t) {
    if (exception instanceof CacheLoaderException) {
      return new CacheLoaderException(txt, exception.getCause());
    } else {
      return new CacheException(txt, exception);
    }
  }

  static final AtomicIntegerFieldUpdater<OperationCompletion> BULK_OP_COUNT =
    AtomicIntegerFieldUpdater.newUpdater(OperationCompletion.class, "countDown");

  static final AtomicIntegerFieldUpdater<OperationCompletion> BULK_OP_EXCEPTION_COUNT =
    AtomicIntegerFieldUpdater.newUpdater(OperationCompletion.class, "exceptionCount");

  static final AtomicReferenceFieldUpdater<OperationCompletion, Throwable> BULK_OP_EXCEPTION =
    AtomicReferenceFieldUpdater.newUpdater(OperationCompletion.class, Throwable.class,
      "exception");

}
