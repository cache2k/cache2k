package org.cache2k.storage;

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

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * @author Jens Wilke
 */
@SuppressWarnings("unused")
public class StandardStorageEventCounter implements StorageEventCounter {

  final static AtomicLongFieldUpdater<StandardStorageEventCounter> READ_MISS_UPDATER =
    AtomicLongFieldUpdater.newUpdater(StandardStorageEventCounter.class, "readMiss");
  private volatile long readMiss;
  @Override
  public void readMiss() {
    READ_MISS_UPDATER.incrementAndGet(this);
  }
  @Override
  public void readMiss(long cnt) { READ_MISS_UPDATER.addAndGet(this, cnt); }
  @Override
  public long getReadMiss() {
    return READ_MISS_UPDATER.get(this);
  }

}
