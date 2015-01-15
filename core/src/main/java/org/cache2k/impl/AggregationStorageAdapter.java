package org.cache2k.impl;

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

import org.cache2k.ClosableIterator;
import org.cache2k.impl.threading.Futures;
import org.cache2k.storage.StorageEntry;

import java.util.concurrent.Future;

/**
 * Not used now, aggregation prototype
 *
 * @author Jens Wilke; created: 2014-06-04
 */
public class AggregationStorageAdapter extends StorageAdapter implements StorageAdapter.Parent {

  StorageAdapter.Parent parent;
  BaseCache cache;
  StorageAdapter[] storages;

  @Override
  public void open() {
    for (StorageAdapter a : storages) {
      a.open();
    }
  }

  @Override
  public void flush() {
    for (StorageAdapter a : storages) {
      a.flush();
    }
  }

  @Override
  public void purge() {
    for (StorageAdapter a : storages) {
      a.purge();
    }
  }

  @Override
  public Future<Void> shutdown() {
    Futures.WaitForAllFuture<Void> _waitForAll = new Futures.WaitForAllFuture<Void>();
    for (StorageAdapter a : storages) {
      _waitForAll.add(a.shutdown());
    }
    return _waitForAll;
  }

  @Override
  public boolean checkStorageStillDisconnectedForClear() {
    boolean _flag = true;
    for (StorageAdapter a : storages) {
      _flag &= a.checkStorageStillDisconnectedForClear();
    }
    return _flag;
  }

  @Override
  public void disconnectStorageForClear() {
    for (StorageAdapter a : storages) {
      a.disconnectStorageForClear();
    }
  }

  @Override
  public Future<Void> clearAndReconnect() {
    Futures.WaitForAllFuture<Void> _waitForAllFuture = new Futures.WaitForAllFuture<Void>();
    for (StorageAdapter a : storages) {
      _waitForAllFuture.add(a.clearAndReconnect());
    }
    return _waitForAllFuture;
  }

  /**
   * Send put to all storage instances. Delegated instance can will decide whether
   * it really puts or not (passivation / no - passivation). The put is called
   * sequentially.
   */
  @Override
  public void put(Entry e, long _nextRefreshTime) {
    for (StorageAdapter a : storages) {
      a.put(e, _nextRefreshTime);
    }
  }

  /**
   * Ask storages for the entry. The get is tried in the order of the storages until
   * an entry is found.
   */
  @Override
  public StorageEntry get(Object key) {
    for (StorageAdapter a : storages) {
      StorageEntry e = a.get(key);
      if (e != null) {
        return e;
      }
    }
    return null;
  }

  @Override
  public boolean remove(Object key) {
    boolean f = false;
    for (StorageAdapter a : storages) {
      f |= a.remove(key);
    }
    return f;
  }

  @Override
  public void evict(Entry e) {
    for (StorageAdapter a : storages) {
      a.evict(e);
    }
  }

  @Override
  public void expire(Entry e) {
    for (StorageAdapter a : storages) {
      a.expire(e);
    }
  }

  /**
   * Use the last storage, which should be the biggest and contain
   * all entries.
   */
  @Override
  public ClosableIterator<Entry> iterateAll() {
    return storages[storages.length - 1].iterateAll();
  }

  @Override
  public int getTotalEntryCount() {
    int cnt = 0;
    for (StorageAdapter a : storages) {
      cnt = Math.max(a.getTotalEntryCount(), cnt);
    }
    return cnt;
  }

  @Override
  public int getAlert() {
    int _level = 0;
    for (StorageAdapter a : storages) {
      if (a.getAlert() > _level) {
        _level = a.getAlert();
      }
    }
    return _level;
  }

  /** Bad thing occurred, we do not what storage is to be blamed. Disable completely */
  @Override
  public void disable(Throwable t) {
    synchronized (cache.lock) {
      parent.resetStorage(this, new NoopStorageAdapter(cache));
      parent = null;
      for (StorageAdapter a : storages) {
        a.disable(t);
      }
    }
  }

  @Override
  public void resetStorage(final StorageAdapter _current, final StorageAdapter _new) {
    synchronized (cache.lock) {
      if (parent == null) { return; }
      if (_new instanceof NoopStorageAdapter) {
        if (storages.length == 1) {
          parent.resetStorage(this, _new);
        } else {
          final StorageAdapter[] sa = new StorageAdapter[storages.length - 1];
          synchronized (sa) {
            int i = 0;
            for (StorageAdapter a : storages) {
              if (a != _current) {
                sa[i++] = a;
              }
            }
          }
          storages = sa;
        }
      } else {
        final StorageAdapter[] sa = new StorageAdapter[storages.length];
        synchronized (sa) {
          int i = 0;
          for (StorageAdapter a : storages) {
            if (a != _current) {
              sa[i++] = a;
            } else {
              sa[i++] = _new;
            }
          }
        }
        storages = sa;
      }
    }
  }

  @Override
  public Future<Void> cancelTimerJobs() {
    Futures.WaitForAllFuture<Void> w = new Futures.WaitForAllFuture<Void>();
    for (StorageAdapter s : storages) {
      Future<Void> f = s.cancelTimerJobs();
      if (f != null) {
        w.add(f);
      }
    }
    return w;
  }

}
