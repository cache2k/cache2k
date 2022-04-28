package org.cache2k.core.operation;

/*-
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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

/**
 * Defines the semantic of a cache operation based on Java code that examines the
 * cached entry and calls {@link Progress} for instructing the cache what needs to be done.
 *
 * @author Jens Wilke
 */
public interface Semantic<K, V, R> {

  /**
   * Start of the operation. Either calls {@link Progress#wantData()} or
   * {@link Progress#wantMutation()}. A {@link Progress#wantMutation()}
   * at the start means we don't need the current data and will mutate the
   * entry unconditionally.
   */
  void start(K key, Progress<K, V, R> c);

  /**
   * Called with the entry containing the recent content. If this method finishes with
   * {@link Progress#wantMutation()} it will be called again after the entry is locked
   * for mutation to reevaluate the examination after concurrent operations on the
   * same entry have completed.
   */
  void examine(K key, Progress<K, V, R> c, ExaminationEntry<K, V> e);

  /**
   * Perform the mutation. The mutation is done by calling the methods on {@link Progress}.
   */
  void mutate(K key, Progress<K, V, R> c, ExaminationEntry<K, V> e);

  /**
   * Load is complete.
   */
  void loaded(K key, Progress<K, V, R> c, ExaminationEntry<K, V> e);

  /**
   * Base class to provide a default for the load result.
   */
  abstract class Base<K, V, R> implements Semantic<K, V, R> {

    /**
     * By default a load returns always the value as result. May be overridden to change.
     * This is only called when a load was requested.
     *
     * @param key
     * @param e the entry containing the old data
     */
    @SuppressWarnings("unchecked")
    @Override
    public void loaded(K key, Progress<K, V, R> c, ExaminationEntry<K, V> e) {
      c.result((R) e.getValueOrExceptionNoAccess());
    }

  }

  /**
   * Only update the entry. This does not need the entry to be present in the heap.
   */
  abstract class InsertOrUpdate<K, V, R> extends Base<K, V, R> {

    /** Instruct to lock the entry for the update. */
    @Override
    public final void start(K key, Progress<K, V, R> c) {
      c.wantMutation();
    }

    /**
     * Instruct to lock the entry for the update. Again, since examine may be called when
     * entry is there.
     */
    @Override
    public final void examine(K key, Progress<K, V, R> c, ExaminationEntry<K, V> e) {
      c.wantMutation();
    }

  }

  /**
   * Read a cache entry and do an optional mutation.
   */
  abstract class MightUpdate<K, V, R> extends Base<K, V, R> {

    /** Reqeust latest data */
    @Override
    public final void start(K key, Progress<K, V, R> c) {
      c.wantData();
    }

  }

  /**
   * Read a cache entry and do an mutation maybe based on the existing values.
   */
  abstract class Update<K, V, R> extends MightUpdate<K, V, R> {

    /** Unconditionally request mutation lock. */
    @Override
    public final void examine(K key, Progress<K, V, R> c, ExaminationEntry<K, V> e) { c.wantMutation(); }

  }

  /**
   * Read only operation.
   */
  abstract class Read<K, V, R> extends Base<K, V, R> {

    /** Instruct to provide the cache content */
    @Override
    public final void start(K key, Progress<K, V, R> c) {
      c.wantData();
    }

    /** No operation. */
    @Override
    public final void mutate(K key, Progress<K, V, R> c, ExaminationEntry<K, V> e) {
    }

  }

}
