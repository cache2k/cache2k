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
import org.cache2k.Cache2kBuilder;
import org.junit.After;
import org.junit.Before;

import static org.junit.Assert.assertNull;

/**
 * Execute the generic spring tests
 *
 * @author Jens Wilke
 */
public class SpringCache2kCacheTest extends AbstractCacheTests<Cache2kCache> {

  private Cache nativeCache;
  private Cache2kCache cache;

  @Before
  public void setUp() {
    Cache2kBuilder builder = Cache2kCacheManager.configureCache(Cache2kBuilder.forUnknownTypes().name(CACHE_NAME));
    cache = Cache2kCacheManager.buildAndWrap(builder);
    nativeCache = cache.getNativeCache();
  }

  @After
  public void tearDown() {
    nativeCache.close();
  }

  @Override
  protected Cache2kCache getCache() {
    return cache;
  }

  @Override
  protected Cache getNativeCache() {
    return nativeCache;
  }

}
