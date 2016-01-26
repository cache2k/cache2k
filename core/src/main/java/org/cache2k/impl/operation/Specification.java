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

import org.cache2k.CacheEntry;
import org.cache2k.impl.ExceptionWrapper;

/**
 * Semantics of all cache operations on entries.
 *
 * @author Jens Wilke
 */
@SuppressWarnings({"unchecked", "UnusedParameters"})
public class Specification<K, V> {

  public Semantic<K, V, V> peek(K key) {
    return PEEK;
  }
  static final Semantic PEEK = new Semantic.Read() {
    @Override
    public void examine(Progress c, ExaminationEntry e) {
      if (c.isPresentOrMiss()) {
        c.result(e.getValueOrException());
      }
    }
  };

  public Semantic<K, V, V> get(K key) {
    return GET;
  }

  static final Semantic GET = new Semantic.MightUpdateExisting() {

    @Override
    public void examine(Progress c, ExaminationEntry e) {
      if (c.isPresentOrMiss()) {
        c.result(e.getValueOrException());
      } else {
        c.wantMutation();
      }
    }

    @Override
    public void update(Progress c, ExaminationEntry e) {
        /* after the mutation lock is acquired the entry needs to be
           checked again, since other operations may have completed.
           the thundering herd is just stalled in front of the lock.
           however, the general processing scheme calls examine again, after
           the locking, so we can safe the additional code.
        if (e.hasFreshData()) {
          c.finish(e.getValueOrException());
          return;
        }
        */
      c.load();
    }
  };

  public Semantic<K, V, ResultEntry<K,V>> getEntry(K key) {
    return GET_ENTRY;
  }

  static final Semantic GET_ENTRY = new Semantic.MightUpdateExisting() {

    @Override
    public void examine(Progress c, ExaminationEntry e) {
      if (c.isPresentOrMiss()) {
        c.result(e.getValueOrException());
      } else {
        c.wantMutation();
      }
    }

    @Override
    public void update(final Progress c, final ExaminationEntry e) {
      c.load();
    }

    @Override
    public void loaded(final Progress c, final ExaminationEntry e, final Object _newValueOrException) {
      c.result(returnStableEntry(e));
      throw new UnsupportedOperationException();
    }
  };

  static CacheEntry returnStableEntry(ExaminationEntry e) {
    return
      new ReadOnlyCacheEntry(
        e.getKey(), e.getValueOrException(),
        e.getLastModification());
  }

  public Semantic<K, V, ResultEntry<K,V>> peekEntry(K key) {
    return PEEK_ENTRY;
  }

  static final Semantic PEEK_ENTRY = new Semantic.UpdateExisting() {

    @Override
    public void update(final Progress c, final ExaminationEntry e) {
      if (c.isPresentOrMiss()) {
        c.result(returnStableEntry(e));
      }
    }

  };

  public Semantic<K, V, V> remove(K key) {
    return REMOVE;
  }

  static final Semantic REMOVE = new Semantic.Update() {

    @Override
    public void update(Progress c, ExaminationEntry e) {
      c.remove();
    }
  };

  public Semantic<K, V, Boolean> containsAndRemove(K key) {
    return CONTAINS_REMOVE;
  }

  static final Semantic CONTAINS_REMOVE = new Semantic.UpdateExisting() {

    @Override
    public void update(Progress c, ExaminationEntry e) {
      if (c.isPresent()) {
        c.result(true);
        c.remove();
        return;
      }
      c.result(false);
      c.remove();
    }
  };

  public Semantic<K, V, Boolean> contains(K key) {
    return CONTAINS;
  }

  static final Semantic CONTAINS = new Semantic.Read() {

    @Override
    public void examine(Progress c, ExaminationEntry e) {
      if (c.isPresent()) {
        c.result(true);
        return;
      }
      c.result(false);
    }
  };

  public Semantic<K, V, V> peekAndRemove(K key) {
    return PEEK_REMOVE;
  }

  static final Semantic PEEK_REMOVE = new Semantic.UpdateExisting() {

    @Override
    public void update(Progress c, ExaminationEntry e) {
      if (c.isPresentOrMiss()) {
        c.result(e.getValueOrException());
      }
      c.remove();
    }
  };

  public Semantic<K, V, V> peekAndReplace(final K key, final V value) {
    return new Semantic.UpdateExisting<K, V, V>() {

      @Override
      public void update(Progress<V, V> c, ExaminationEntry<K, V> e) {
        if (c.isPresentOrMiss()) {
          c.result(e.getValueOrException());
          c.put(value);
        }
      }

    };
  }

  public Semantic<K, V, V> peekAndPut(final K key, final V value) {
    return new Semantic.UpdateExisting<K, V, V>() {

      @Override
      public void update(Progress<V, V> c, ExaminationEntry<K, V> e) {
        if (c.isPresentOrMiss()) {
          c.result(e.getValueOrException());
        }
        c.put(value);
      }

    };
  }

  public Semantic<K, V, V> put(final K key, final V value) {
    return new Semantic.Update<K, V, V>() {

      @Override
      public void update(Progress<V, V> c, ExaminationEntry<K, V> e) {
        c.put(value);
      }

    };
  }

  public Semantic<K, V, Boolean> putIfAbsent(final K key, final V value) {
    return new Semantic.UpdateExisting<K, V, Boolean>() {

      @Override
      public void update(Progress<V, Boolean> c, ExaminationEntry<K, V> e) {
        if (!c.isPresent()) {
          c.result(true);
          c.put(value);
          return;
        }
        c.result(false);
      }

    };
  }

  public Semantic<K, V, Boolean> replace(final K key, final V value) {
    return new Semantic.UpdateExisting<K, V, Boolean>() {

      @Override
      public void update(Progress<V, Boolean> c, ExaminationEntry<K, V> e) {
        if (c.isPresent()) {
          c.result(true);
          c.put(value);
          return;
        }
        c.result(false);
      }

    };
  }

  public Semantic<K, V, Boolean> replace(final K key, final V value, final V newValue) {
    return new Semantic.UpdateExisting<K, V, Boolean>() {

      @Override
      public void update(Progress<V, Boolean> c, ExaminationEntry<K, V> e) {
        if (c.isPresentOrMiss() && e.getValueOrException().equals(value)) {
          c.result(true);
          c.put(newValue);
          return;
        }
        c.result(false);
      }

    };
  }

  public Semantic<K, V, CacheEntry<K,V>> replaceOrGet(final K key, final V value,
                                                      final V newValue, final CacheEntry<K, V> dummyEntry) {
    return new Semantic.UpdateExisting<K, V, CacheEntry<K,V>>() {

      @Override
      public void update(Progress<V, CacheEntry<K, V>> c, ExaminationEntry<K, V> e) {
        if (c.isPresentOrMiss()) {
          if (e.getValueOrException().equals(value)) {
            c.result(null);
            c.put(newValue);
            return;
          }
          c.result(returnStableEntry(e));
          return;
        }
        c.result(dummyEntry);
      }

    };
  }

  public Semantic<K, V, Boolean> remove(final K key, final V value) {
    return new Semantic.UpdateExisting<K, V, Boolean>() {

      @Override
      public void update(Progress<V, Boolean> c, ExaminationEntry<K, V> e) {
        if (c.isPresentOrMiss() && e.getValueOrException().equals(value)) {
          c.result(true);
          c.remove();
          return;
        }
        c.result(false);
      }

    };
  }

  static class ReadOnlyCacheEntry<K, V> implements ResultEntry<K, V> {

    K key;
    V valueOrException;
    long lastModification;

    public ReadOnlyCacheEntry(final K _key, final V _valueOrException, final long _lastModification) {
      key = _key;
      lastModification = _lastModification;
      valueOrException = _valueOrException;
    }

    @Override
    public Throwable getException() {
      if (valueOrException instanceof ExceptionWrapper) {
        return ((ExceptionWrapper) valueOrException).getException();
      }
      return null;
    }

    @Override
    public K getKey() {
      return key;
    }

    @Override
    public long getLastModification() {
      return lastModification;
    }

    @Override
    public V getValue() {
      if (valueOrException instanceof ExceptionWrapper) {
        return null;
      }
      return valueOrException;
    }

    @Override
    public V getValueOrException() {
      return valueOrException;
    }

  }

}
