package org.cache2k.storage;

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

import java.util.Properties;

/**
 * Expose the needed information and interactions with a cache
 * to a cache storage.
 *
 * @author Jens Wilke; created: 2014-04-19
 */
public interface CacheStorageContext {

  Properties getProperties();

  String getManagerName();

  String getCacheName();

  /**
   * Type of the cache key. The storage can use this information to optimize its
   * operation or completely ignore it.
   */
  Class<?> getKeyType();

  /**
   * Type of the cache value. The storage can use this information to optimize its
   * operation or completely ignore it.
   */
  Class<?> getValueType();

  /**
   * A marshaller factory the storage may use.
   *
   */
  MarshallerFactory getMarshallerFactory();

  void requestMaintenanceCall(int _intervalMillis);

  void notifyEvicted(StorageEntry e);

  void notifyExpired(StorageEntry e);

}
