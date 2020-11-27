package org.cache2k.core.operation;

/*
 * #%L
 * cache2k core implementation
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

import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.io.CacheLoaderException;
import org.cache2k.processor.EntryProcessingException;
import org.cache2k.processor.EntryProcessor;
import org.cache2k.processor.RestartException;

import java.util.function.Function;

/**
 * Semantics of all cache operations on entries.
 *
 * @author Jens Wilke
 */
@SuppressWarnings({"unchecked", "UnusedParameters"})
public class Operations<K, V> {

  public static final Operations SINGLETON = new Operations();

  public Semantic<K, V, V> peek(K key) {
    return PEEK;
  }
  static final Semantic PEEK = new Semantic.Read() {
    @Override
    public void examine(Progress c, ExaminationEntry e) {
      if (c.isDataFreshOrMiss()) {
        c.result(e.getValueOrException());
      }
      c.noMutation();
    }
  };

  public Semantic<K, V, V> get(K key) {
    return GET;
  }

  public static final Semantic GET = new Semantic.MightUpdate() {

    @Override
    public void examine(Progress c, ExaminationEntry e) {
      if (c.isDataFreshOrMiss()) {
        c.result(e.getValueOrException());
        c.noMutation();
      } else {
        if (c.isLoaderPresent()) {
          c.wantMutation();
        } else {
          c.noMutation();
        }
      }
    }

    @Override
    public void mutate(Progress c, ExaminationEntry e) {
      c.load();
    }
  };

  public final Semantic unconditionalLoad = new Semantic.InsertOrUpdate() {
    @Override
    public void mutate(Progress c, ExaminationEntry e) {
      c.load();
    }
  };

  public final Semantic<K, V, Void> refresh = new Semantic.MightUpdate<K, V, Void>() {

    /**
     * Only refresh if the entry is still existing. If an entry is deleted before the
     * refresh, the refresh should do nothing.
     */
    @Override
    public void examine(Progress<K, V, Void> c, ExaminationEntry<K, V> e) {
      if (c.isDataFreshOrRefreshing() || c.isExpiryTimeReachedOrInRefreshProbation()) {
        c.wantMutation();
      } else {
        c.noMutation();
      }
    }

    @Override
    public void mutate(Progress<K, V, Void> c, ExaminationEntry<K, V> e) {
      c.refresh();
    }
  };

  public Semantic<K, V, ResultEntry<K, V>> getEntry(K key) {
    return GET_ENTRY;
  }

  static final Semantic GET_ENTRY = new Semantic.MightUpdate() {

    @Override
    public void examine(Progress c, ExaminationEntry e) {
      if (c.isDataFreshOrMiss()) {
        c.entryResult(e);
        c.noMutation();
      } else {
        if (c.isLoaderPresent()) {
          c.wantMutation();
        } else {
          c.noMutation();
        }
      }
    }

    @Override
    public void mutate(Progress c, ExaminationEntry e) {
      c.load();
    }

    @Override
    public void loaded(Progress c, ExaminationEntry e) {
      c.entryResult(e);
    }
  };

  public Semantic<K, V, ResultEntry<K, V>> peekEntry(K key) {
    return PEEK_ENTRY;
  }

  static final Semantic PEEK_ENTRY = new Semantic.Read() {

    @Override
    public void examine(Progress c, ExaminationEntry e) {
      if (c.isDataFreshOrMiss()) {
        c.entryResult(e);
      }
      c.noMutation();
    }

  };

  public Semantic<K, V, V> remove(K key) {
    return REMOVE;
  }

  static final Semantic REMOVE = new Semantic.InsertOrUpdate() {

    @Override
    public void mutate(Progress c, ExaminationEntry e) {
      c.remove();
    }
  };

  public Semantic<K, V, Boolean> containsAndRemove(K key) {
    return CONTAINS_REMOVE;
  }

  static final Semantic CONTAINS_REMOVE = new Semantic.Update() {

    @Override
    public void mutate(Progress c, ExaminationEntry e) {
      if (c.isDataFresh()) {
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
      c.result(c.isDataFresh());
      c.noMutation();
    }

  };

  public Semantic<K, V, V> peekAndRemove(K key) {
    return PEEK_REMOVE;
  }

  static final Semantic PEEK_REMOVE = new Semantic.Update() {

    @Override
    public void mutate(Progress c, ExaminationEntry e) {
      if (c.isDataFreshOrMiss()) {
        c.result(e.getValueOrException());
      }
      c.remove();
    }
  };

  public Semantic<K, V, V> peekAndReplace(K key, V value) {
    return new Semantic.MightUpdate<K, V, V>() {

      @Override
      public void examine(Progress<K, V, V> c, ExaminationEntry<K, V> e) {
        if (c.isDataFreshOrMiss()) {
          c.result(e.getValueOrException());
          c.wantMutation();
          return;
        }
        c.noMutation();
      }

      @Override
      public void mutate(Progress<K, V, V> c, ExaminationEntry<K, V> e) {
        c.put(value);
      }

    };
  }

  public Semantic<K, V, V> peekAndPut(K key, V value) {
    return new Semantic.Update<K, V, V>() {

      @Override
      public void mutate(Progress<K, V, V> c, ExaminationEntry<K, V> e) {
        if (c.isDataFreshOrMiss()) {
          c.result(e.getValueOrException());
        }
        c.put(value);
      }

    };
  }

  public Semantic<K, V, V> computeIfAbsent(K key, Function<? super K, ? extends V> function) {
    return new Semantic.MightUpdate<K, V, V>() {

      @Override
      public void examine(Progress<K, V, V> c, ExaminationEntry<K, V> e) {
        if (c.isDataFreshOrMiss()) {
          c.result(e.getValueOrException());
          c.noMutation();
        } else {
          c.wantMutation();
        }
      }

      @Override
      public void mutate(Progress<K, V, V> c, ExaminationEntry<K, V> e) {
        try {
          V value = function.apply(e.getKey());
          c.result(value);
          c.put(value);
        } catch (RuntimeException ex) {
          c.failure(ex);
        } catch (Exception ex) {
          c.failure(new CacheLoaderException(ex));
        }
      }

    };
  }

  public Semantic<K, V, V> put(K key, V value) {
    return new Semantic.InsertOrUpdate<K, V, V>() {

      @Override
      public void mutate(Progress<K, V, V> c, ExaminationEntry<K, V> e) {
        c.put(value);
      }

    };
  }

  /**
   * Updates intentionally hit and miss counter to adjust with JSR107.
   *
   * @see org.cache2k.Cache#putIfAbsent(Object, Object)
   */
  public Semantic<K, V, Boolean> putIfAbsent(K key, V value) {
    return new Semantic.MightUpdate<K, V, Boolean>() {

      @Override
      public void examine(Progress<K, V, Boolean> c, ExaminationEntry<K, V> e) {
        if (!c.isDataFreshOrMiss()) {
          c.result(true);
          c.wantMutation();
        } else {
          c.result(false);
          c.noMutation();
        }
      }

      @Override
      public void mutate(Progress<K, V, Boolean> c, ExaminationEntry<K, V> e) {
        c.put(value);
      }

    };
  }

  /**
   * Updates intentionally hit and miss counter to adjust with JSR107.
   *
   * @see org.cache2k.Cache#replace(Object, Object)
   */
  public Semantic<K, V, Boolean> replace(K key, V value) {
    return new Semantic.MightUpdate<K, V, Boolean>() {

      @Override
      public void examine(Progress<K, V, Boolean> c, ExaminationEntry<K, V> e) {
        if (c.isDataFreshOrMiss()) {
          c.result(true);
          c.wantMutation();
        } else {
          c.result(false);
          c.noMutation();
        }
      }

      @Override
      public void mutate(Progress<K, V, Boolean> c, ExaminationEntry<K, V> e) {
        c.put(value);
      }

    };
  }

  public Semantic<K, V, Boolean> replace(K key, V value, V newValue) {
    return new Semantic.MightUpdate<K, V, Boolean>() {

      @Override
      public void examine(Progress<K, V, Boolean> c, ExaminationEntry<K, V> e) {
        if (c.isDataFreshOrMiss() &&
          ((value ==  e.getValueOrException()) ||
            (value != null && value.equals(e.getValueOrException())))) {
          c.result(true);
          c.wantMutation();
        } else {
          c.result(false);
          c.noMutation();
        }
      }

      @Override
      public void mutate(Progress<K, V, Boolean> c, ExaminationEntry<K, V> e) {
        c.put(newValue);
      }

    };
  }

  public Semantic<K, V, Boolean> remove(K key, V value) {
    return new Semantic.MightUpdate<K, V, Boolean>() {

      @Override
      public void examine(Progress<K, V, Boolean> c, ExaminationEntry<K, V> e) {
        if (c.isDataFreshOrMiss() &&
          ((value == null && e.getValueOrException() == null) ||
            value.equals(e.getValueOrException()))) {
          c.result(true);
          c.wantMutation();
        } else {
          c.result(false);
          c.noMutation();
        }
      }

      @Override
      public void mutate(Progress<K, V, Boolean> c, ExaminationEntry<K, V> e) {
        c.remove();
      }

    };
  }

  public <R> Semantic<K, V, R> invoke(K key, EntryProcessor<K, V, R> processor) {
    return new Semantic.Base<K, V, R>() {

      private MutableEntryOnProgress<K, V> mutableEntryResult;
      private boolean needsLoad;
      /** Triggers wantData and wantMutate which leads to a lock */
      private boolean needsLock;

      @Override
      public void start(Progress<K, V, R> c) {
        MutableEntryOnProgress<K, V> mutableEntry =
          new MutableEntryOnProgress<K, V>(key, c, null, false);
        try {
          R result = processor.process(mutableEntry);
          c.result(result);
        } catch (WantsDataRestartException rs) {
          c.wantData();
          return;
        } catch (NeedsLockRestartException ex) {
          needsLock = true;
          c.wantData();
          return;
        } catch (Throwable t) {
          c.failure(new EntryProcessingException(t));
          return;
        }
        maybeMutate(c, mutableEntry);
      }

      @Override
      public void examine(Progress<K, V, R> c, ExaminationEntry<K, V> e) {
        if (needsLock) {
          c.wantMutation();
          return;
        }
        MutableEntryOnProgress<K, V> mutableEntry =
          new MutableEntryOnProgress<K, V>(key, c, e, false);
        try {
          R result = processor.process(mutableEntry);
          c.result(result);
        } catch (NeedsLockRestartException ex) {
          c.wantMutation();
          return;
        } catch (NeedsLoadRestartException rs) {
          needsLoad = true;
          c.wantMutation();
          return;
        } catch (Throwable t) {
          c.failure(new EntryProcessingException(t));
          return;
        }
        maybeMutate(c, mutableEntry);
      }

      private void maybeMutate(Progress<K, V, R> c, MutableEntryOnProgress<K, V> mutableEntry) {
        if (mutableEntry.isMutationNeeded()) {
          mutableEntryResult = mutableEntry;
          c.wantMutation();
        } else {
          c.noMutation();
        }
      }

      @Override
      public void mutate(Progress<K, V, R> c, ExaminationEntry<K, V> e) {
        if (needsLoad) {
          needsLoad = false;
          c.loadAndRestart();
          return;
        }
        if (mutableEntryResult == null) {
          mutableEntryResult =
            new MutableEntryOnProgress<K, V>(key, c, e, true);
          try {
            R result = processor.process(mutableEntryResult);
            c.result(result);
          } catch (Throwable t) {
            c.failure(new EntryProcessingException(t));
            return;
          }
        }
        mutableEntryResult.sendMutationCommand();
      }

      /** No operation, result is set by the entry processor */
      @Override
      public void loaded(Progress<K, V, R> c, ExaminationEntry<K, V> e) { }
    };
  }

  public static class WantsDataRestartException extends RestartException { }

  public static class NeedsLoadRestartException extends RestartException { }

  public static class NeedsLockRestartException extends RestartException { }

  public Semantic<K, V, Void> expire(K key, long time) {
    return new Semantic.MightUpdate<K, V, Void>() {

      @Override
      public void examine(Progress<K, V, Void> c, ExaminationEntry<K, V> e) {
        if (c.isDataFreshOrRefreshing()) {
          c.wantMutation();
        } else {
          c.noMutation();
        }
      }

      @Override
      public void mutate(Progress c, ExaminationEntry e) {
        c.expire(time);
      }
    };
  }

  public final Semantic<K, V, Void> expireEvent = new ExpireEvent<K, V>();

  public static class ExpireEvent<K, V> extends Semantic.MightUpdate<K, V, Void> {

    @Override
    public void examine(Progress<K, V, Void> c, ExaminationEntry<K, V> e) {
      if (c.isExpiryTimeReachedOrInRefreshProbation()) {
        c.wantMutation();
        return;
      }
      c.noMutation();
    }

    @Override
    public void mutate(Progress<K, V, Void> c, ExaminationEntry<K, V> e) {
      c.expire(ExpiryTimeValues.NOW);
    }
  }

}
