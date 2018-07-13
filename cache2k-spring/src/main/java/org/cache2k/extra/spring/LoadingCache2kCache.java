package org.cache2k.extra.spring;

/*
 * #%L
 * cache2k JCache provider
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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
import org.cache2k.integration.CacheLoaderException;

import java.util.concurrent.Callable;

/**
 * @author Jens Wilke
 */
public class LoadingCache2kCache extends Cache2kCache {

  public LoadingCache2kCache(final Cache<Object, Object> cache) {
    super(cache);
  }

  /**
   * <p>Ignore the {@code valueLoader} parameter in case a loader is present. This makes
   * sure the loader is consistently used and we make use of the cache2k features
   * refresh ahead and resilience.
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T get(final Object key, final Callable<T> valueLoader) {
    return (T) cache.get(key);
  }

  public boolean isLoaderPresent() {
    return true;
  }

}
