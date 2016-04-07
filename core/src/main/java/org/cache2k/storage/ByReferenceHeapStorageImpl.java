package org.cache2k.storage;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import org.cache2k.StorageConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple storage implementation, just uses a hashmap. Usable for testing and as a
 * tiny storage implementation example.
 *
 * @author Jens Wilke; created: 2014-06-21
 */
public class ByReferenceHeapStorageImpl implements CacheStorage {

  StorageConfiguration<Void> config;
  CacheStorageContext context;
  HashMap<Object, HeapEntry> entries;

  public void open(CacheStorageContext ctx, StorageConfiguration<Void> cfg) {
    context = ctx;
    config = cfg;
    final int entryCapacity = cfg.getEntryCapacity();
    if (entryCapacity == Integer.MAX_VALUE) {
      entries = new HashMap<Object, HeapEntry>();
    } else {
      entries = new LinkedHashMap<Object, HeapEntry>(100, .75F, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<Object, HeapEntry> _eldest) {
          if (getEntryCount() > entryCapacity) {
            context.notifyExpired(_eldest.getValue());
            return true;
          }
          return false;
        }
      };
    }

  }

  @Override
  public synchronized StorageEntry get(Object key) throws Exception {
    return entries.get(key);
  }

  @Override
  public void put(StorageEntry e) throws Exception {
    HeapEntry he = new HeapEntry();
    he.key = e.getKey();
    he.value = e.getValueOrException();
    he.updated = e.getCreatedOrUpdated();
    he.entryExpiry = e.getEntryExpiryTime();
    he.valueExpiry = e.getValueExpiryTime();
    synchronized (this) {
      entries.put(e.getKey(), he);
    }
  }

  @Override
  public synchronized boolean remove(Object key) throws Exception {
    return entries.remove(key) != null;
  }

  @Override
  public synchronized boolean contains(Object key) throws Exception {
    return entries.containsKey(key);
  }

  @Override
  public synchronized void clear() throws Exception {
    entries.clear();
  }

  @Override
  public void close() throws Exception {
    entries = null;
  }

  @Override
  public void visit(VisitContext ctx, EntryFilter f, EntryVisitor v) throws Exception {
    List<StorageEntry> l = new ArrayList<StorageEntry>();
    synchronized (this) {
      for (StorageEntry e : entries.values()) {
        if (f.shouldInclude(e.getKey())) {
          l.add(e);
        }
      }
    }
    for (StorageEntry e : l) {
      if (f.shouldInclude(e.getKey())) {
        v.visit(e);
      }
    }
  }

  @Override
  public synchronized int getEntryCount() {
    return entries.size();
  }

  static class HeapEntry implements StorageEntry {
    Object key;
    Object value;
    long updated;
    long valueExpiry;
    long entryExpiry;

    @Override
    public Object getKey() {
      return key;
    }

    @Override
    public Object getValueOrException() {
      return value;
    }

    @Override
    public long getCreatedOrUpdated() {
      return updated;
    }

    @Override
    public long getValueExpiryTime() {
      return valueExpiry;
    }

    @Override
    public long getEntryExpiryTime() {
      return entryExpiry;
    }
  }

  public static class Provider
    extends CacheStorageProviderWithVoidConfig implements ByReferenceHeapStorage {

    @Override
    public ByReferenceHeapStorageImpl create(CacheStorageContext ctx, StorageConfiguration<Void> cfg) {
      ByReferenceHeapStorageImpl st = new ByReferenceHeapStorageImpl();
      st.open(ctx, cfg);
      return st;
    }

  }

}
