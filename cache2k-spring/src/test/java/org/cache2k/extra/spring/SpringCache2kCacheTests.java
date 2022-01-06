package org.cache2k.extra.spring;

/*-
 * #%L
 * cache2k Spring framework support
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
import org.junit.After;
import org.junit.Before;

import static org.junit.Assert.assertNull;

/**
 * Execute spring test suite.
 *
 * @author Jens Wilke
 */
public class SpringCache2kCacheTests extends AbstractCacheTests<SpringCache2kCache> {

  private Cache nativeCache;
  private SpringCache2kCache cache;

  @Before
  public void setUp() {
    SpringCache2kCacheManager mgm = new SpringCache2kCacheManager();
    Cache2kBuilder builder = mgm.getDefaultBuilder().name(CACHE_NAME);
    cache = mgm.buildAndWrap(builder);
    nativeCache = cache.getNativeCache();
  }

  @After
  public void tearDown() {
    nativeCache.close();
  }

  @Override
  protected SpringCache2kCache getCache() {
    return cache;
  }

  @Override
  protected Cache getNativeCache() {
    return nativeCache;
  }

}
