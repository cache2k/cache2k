package org.cache2k.spi;

/*
 * #%L
 * cache2k API
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

import org.cache2k.Cache;
import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.CacheManager;

/**
 * For API internal use only. The cache2k implementation provides the
 * concrete implementations via this interface. Only one active cache2k
 * implementation is supported by the API.
 *
 * <p>Right now, there is only one implementation within the core package.
 * Maybe there will be stripped or extended implementations, or special
 * build implementations, e.g. for Android in the future.
 *
 * <p>This is for internal use by the API to locate the implementation.
 * Do not use or rely on this.
 *
 * @author Jens Wilke; created: 2014-04-20
 */
public abstract class Cache2kCoreProvider {

  public abstract Cache2kManagerProvider getManagerProvider();

  public abstract <K,V> Cache<K,V> createCache(CacheManager m, Cache2kConfiguration<K,V> cfg);

}
