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

import org.cache2k.impl.util.Log;

import java.util.Properties;

/**
 * Expose the needed information and interactions with a cache
 * to a cache storage.
 *
 * @author Jens Wilke; created: 2014-04-19
 */
public interface CacheStorageContext {

  Log getLog();

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
