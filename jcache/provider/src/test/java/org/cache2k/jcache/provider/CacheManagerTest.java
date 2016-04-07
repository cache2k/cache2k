package org.cache2k.jcache.provider;

/*
 * #%L
 * cache2k JCache JSR107 implementation
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
import org.junit.Test;
import static org.junit.Assert.*;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;

/**
 * @author Jens Wilke; created: 2015-03-29
 */
public class CacheManagerTest {

  @Test
  public void testSameProvider() {
    CachingProvider p1 = Caching.getCachingProvider();
    CachingProvider p2 = Caching.getCachingProvider();
    assertTrue(p1 == p2);
  }

  @Test
  public void testSameCacheManager() {
    CachingProvider p = Caching.getCachingProvider();
    CacheManager cm1 = p.getCacheManager();
    CacheManager cm2 = p.getCacheManager();
    assertTrue(cm1 == cm2);
  }

}
