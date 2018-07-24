package org.cache2k.core.operation;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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
   * Start of the operation.
   */
  void start(Progress<K, V, R> c);

  /**
   * Called with the entry containing the recent content. If this method finishes with
   * {@link Progress#wantMutation()} it will be called again after the entry is locked
   * for mutation to reevaluate the examination after other processing completed.
   */
  void examine(Progress<K, V, R> c, ExaminationEntry<K, V> e);

  /**
   * Perform the mutation. The mutation is done by calling the methods on {@link Progress}.
   */
  void update(Progress<K, V, R> c, ExaminationEntry<K, V> e);

  /**
   * Load is complete.
   */
  void loaded(Progress<K, V, R> c, ExaminationEntry<K, V> e);

  /**
   * Base class to provide a default for the load result.
   */
  abstract class Base<K, V, R> implements Semantic<K, V, R> {

    /**
     * By default a load returns always the value as result. May be overridden to change.
     * This is only called when a load was requested.
     *
     * @param e the entry containing the old data
     */
    @SuppressWarnings("unchecked")
    @Override
    public void loaded(final Progress<K, V, R> c, final ExaminationEntry<K, V> e) {
      c.result((R) e.getValueOrException());
    }

  }

  /**
   * Only update the entry. This does not need the entry to be present in the heap.
   */
  abstract class Update<K, V, R> extends Base<K, V, R> {

    /** Instruct to lock the entry for the update. */
    @Override
    public final void start(Progress<K, V, R> c) {
      c.wantMutation();
    }

    /** Instruct to lock the entry for the update. Again, since examine may be called when entry is there. */
    @Override
    public final void examine(Progress<K, V, R> c, ExaminationEntry<K, V> e) {
    }

    /**
     * Called to update the entry.
     *
     * @param e The entry locked for update. The entry value may not represent the latest cache data.
     */
    @Override
    public abstract void update(final Progress<K, V, R> c, final ExaminationEntry<K, V> e);

  }

  /**
   * Read a cache entry and do an optional update. Based on the current state and value of the entry
   * this operation will do an update or not.
   */
  abstract class UpdateExisting<K, V, R> extends Base<K, V, R> {

    /** Request latest data. */
    @Override
    public final void start(Progress<K, V, R> c) {
      c.wantData();
    }

    /** Unconditionally request mutation lock. */
    @Override
    public final void examine(Progress<K, V, R> c, ExaminationEntry<K, V> e) { c.wantMutation(); }

    /** Inspect the element state and issue and update of it. */
    @Override
    public abstract void update(Progress<K, V, R> c, ExaminationEntry<K, V> e);

  }

  /**
   * Read a cache entry and do an optional update. Based on the current state and value of the entry
   * this operation will do an update or not.
   */
  abstract class MightUpdateExisting<K, V, R> extends Base<K, V, R> {

    @Override
    public final void start(Progress<K, V, R> c) {
      c.wantData();
    }

  }

  /**
   * Read only operation.
   */
  abstract class Read<K, V, R> extends Base<K, V, R> {

    /** Instruct to provide the cache content */
    @Override
    public final void start(Progress<K, V, R> c) {
      c.wantData();
    }

    /** No operation. */
    @Override
    public final void update(Progress<K, V, R> c, ExaminationEntry<K, V> e) { }

  }

}
