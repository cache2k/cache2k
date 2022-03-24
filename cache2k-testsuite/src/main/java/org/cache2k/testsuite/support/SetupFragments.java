package org.cache2k.testsuite.support;

/*-
 * #%L
 * cache2k testsuite on public API
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.event.CacheEntryCreatedListener;
import org.cache2k.io.LoadExceptionInfo;
import org.cache2k.io.ResiliencePolicy;

/**
 * @author Jens Wilke
 */
public interface SetupFragments {

  @SuppressWarnings("rawtypes")
  ResiliencePolicy RESILIENCE_CACHE_EXCEPTIONS = new ResiliencePolicy<Object, Object>() {
    @Override
    public long suppressExceptionUntil(Object key, LoadExceptionInfo<Object, Object> loadExceptionInfo,
                                       CacheEntry<Object, Object> cachedEntry) {
      return NOW;
    }

    @Override
    public long retryLoadAfter(Object key, LoadExceptionInfo<Object, Object> loadExceptionInfo) {
      return ETERNAL;
    }
  };

  default void resilienceCacheExceptions(Cache2kBuilder<?, ?> b) {
    b.resiliencePolicy(RESILIENCE_CACHE_EXCEPTIONS);
  }

  default void enableWiredCache(Cache2kBuilder<?, ?> b) {
    b.addListener((CacheEntryCreatedListener) (cache, entry) -> { });
  }

}
