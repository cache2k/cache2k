package org.cache2k.core;

/*
 * #%L
 * cache2k initialization tests
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
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

import org.assertj.core.api.Assertions;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheManager;
import static org.junit.Assert.*;

import org.cache2k.core.log.Log;
import org.junit.Test;

import javax.annotation.Nullable;

/**
 * @author Jens Wilke
 */
public class CacheManagerInitTest {

  public static final @Nullable String OTHER_DEFAULT_CACHE_MANAGER_NAME = "main";

  static {
    CacheManager.setDefaultName(OTHER_DEFAULT_CACHE_MANAGER_NAME);
  }

  public static CacheManager initCacheManager() {
    CacheManager mgm = CacheManager.getInstance();
    assertEquals(OTHER_DEFAULT_CACHE_MANAGER_NAME, mgm.getName());
    return mgm;
  }

  @Test
  public void differentCacheManagerName() {
    assertEquals(CacheManager.getInstance().getName(), OTHER_DEFAULT_CACHE_MANAGER_NAME);
    assertNotEquals(OTHER_DEFAULT_CACHE_MANAGER_NAME, CacheManager.STANDARD_DEFAULT_MANAGER_NAME);
  }

  /**
   * After the cache manager is closed, creating a cache, will create a new cache manager.
   */
  @Test
  public void closeAll() {
    CacheManager cm1 = CacheManager.getInstance();
    CacheManager.closeAll();
    Cache c = Cache2kBuilder.forUnknownTypes().name("xy").build();
    assertNotSame(cm1, c.getCacheManager());
    assertEquals(OTHER_DEFAULT_CACHE_MANAGER_NAME, c.getCacheManager().getName());
    c.close();
  }

  @Test
  public void slf4jInUse() {
    Log l = Log.getLog(CacheManagerInitTest.class);
    Assertions.assertThat(l).isInstanceOf(Log.Slf4jLogger.class);
  }

}
