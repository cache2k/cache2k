package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2014 headissue GmbH, Munich
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

import org.cache2k.storage.StorageEntry;

import java.util.Iterator;

/**
* @author Jens Wilke; created: 2014-05-08
*/
public abstract class StorageAdapter {

  public abstract void open();
  public abstract void shutdown();
  public abstract boolean clearPrepare();
  public abstract void clearProceed();
  public abstract void put(BaseCache.Entry e);
  public abstract StorageEntry get(Object key);
  public abstract void remove(Object key);
  public abstract void evict(BaseCache.Entry e);
  public abstract void expire(BaseCache.Entry e);
  public abstract Iterator<BaseCache.Entry> iterateAll();
  public abstract int getTotalEntryCount();
  /** 0 means no alert, 1 orange, 2, red alert */
  public abstract int getAlert();

}
