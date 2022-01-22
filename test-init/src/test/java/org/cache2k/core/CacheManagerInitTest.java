package org.cache2k.core;

/*-
 * #%L
 * cache2k initialization tests
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

import org.assertj.core.api.Assertions;
import org.cache2k.Cache;
import org.cache2k.CacheManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cache2k.Cache2kBuilder.forUnknownTypes;
import static org.cache2k.CacheManager.STANDARD_DEFAULT_MANAGER_NAME;
import static org.cache2k.CacheManager.getInstance;

import org.cache2k.core.log.Log;
import org.junit.jupiter.api.Test;

/**
 * @author Jens Wilke
 */
public class CacheManagerInitTest {

  public static final String OTHER_DEFAULT_CACHE_MANAGER_NAME = "main";

  static {
    CacheManager.setDefaultName(OTHER_DEFAULT_CACHE_MANAGER_NAME);
  }

  public static void setupOtherDefaultManagerName() { }

  @Test
  public void differentCacheManagerName() {
    assertThat(OTHER_DEFAULT_CACHE_MANAGER_NAME).isEqualTo(getInstance().getName());
    assertThat(STANDARD_DEFAULT_MANAGER_NAME).isNotEqualTo(OTHER_DEFAULT_CACHE_MANAGER_NAME);
  }

  /**
   * After the cache manager is closed, creating a cache, will create a new cache manager.
   */
  @Test
  public void closeAll() {
    CacheManager cm1 = getInstance();
    CacheManager.closeAll();
    Cache c = forUnknownTypes().name("xy").build();
    assertThat(cm1).isNotSameAs(c.getCacheManager());
    assertThat(c.getCacheManager().getName()).isEqualTo(OTHER_DEFAULT_CACHE_MANAGER_NAME);
    c.close();
  }

  @Test
  public void suppressionCounterInUse() {
    Log l = Log.getLog(CacheManagerInitTest.class);
    Assertions.assertThat(l).isInstanceOf(Log.SuppressionCounter.class);
  }

}
