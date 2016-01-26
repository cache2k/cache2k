package org.cache2k.impl.operation;

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
  void start(Progress<V, R> c);

  /**
   * Called with the entry containing the recent content. If this method finishes with
   * {@link Progress#wantMutation()} it will be called again after the entry is locked
   * for mutation to reevaluate the examination after other processing completed.
   */
  void examine(Progress<V, R> c, ExaminationEntry<K, V> e);

  /**
   * Perform the mutation. The mutation is done by calling the methods on {@link Progress}.
   */
  void update(Progress<V, R> c, ExaminationEntry<K, V> e);

  /**
   * Load is complete.
   */
  void loaded(Progress<V, R> c, ExaminationEntry<K, V> e, V v);

  /**
   * Base class to provide a default for the load result.
   */
  abstract class Base<K, V, R> implements Semantic<K, V, R> {

    /**
     * By default a load returns always the value as result. May be overridden to change.
     * This is only called when a load was requested.
     *
     * @param e the entry containing the old data
     * @param _newValueOrException  value returned from the loader
     */
    @Override
    public void loaded(final Progress<V, R> c, final ExaminationEntry<K, V> e, V _newValueOrException) {
      c.result((R) _newValueOrException);
    }

  }

  /**
   * Only update the entry.
   */
  abstract class Update<K, V, R> extends Base<K, V, R> {

    /** Instruct to lock the entry for the update. */
    @Override
    public final void start(Progress<V, R> c) {
      c.wantMutation();
    }

    /** Instruct to lock the entry for the update. Again, since examine may be called when entry is there. */
    @Override
    public final void examine(Progress<V, R> c, ExaminationEntry<K, V> e) { c.wantMutation(); }

    /**
     * Called to update the entry.
     *
     * @param e The entry locked for update. The entry value may not represent the latest cache data.
     */
    @Override
    public abstract void update(final Progress<V, R> c, final ExaminationEntry<K, V> e);

  }

  /**
   * Read a cache entry and do a optional update. Based on the current state and value of the entry
   * this operation will do an update or not.
   */
  abstract class UpdateExisting<K, V, R> extends Base<K, V, R> {

    /** Request latest data. */
    @Override
    public final void start(Progress<V, R> c) {
      c.wantData();
    }

    /** Unconditionally request mutation lock. */
    @Override
    public final void examine(Progress<V, R> c, ExaminationEntry<K, V> e) { c.wantMutation(); }

    /** Inspect the element state and issue and update of it. */
    @Override
    public abstract void update(Progress<V, R> c, ExaminationEntry<K, V> e);

  }

  /**
   * Read a cache entry and do a optional update. Based on the current state and value of the entry
   * this operation will do an update or not.
   */
  abstract class MightUpdateExisting<K, V, R> extends Base<K, V, R> {

    @Override
    public final void start(Progress<V, R> c) {
      c.wantData();
    }

  }

  /**
   * Read only operation.
   */
  abstract class Read<K, V, R> extends Base<K, V, R> {

    /** Instruct to provide the cache content */
    @Override
    public final void start(Progress<V, R> c) {
      c.wantData();
    }

    /** No operation. */
    @Override
    public final void update(Progress<V, R> c, ExaminationEntry<K, V> e) { }

  }

}
