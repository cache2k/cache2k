package org.cache2k.jcache.tests;

/*
 * #%L
 * cache2k JCache tests
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

import org.jsr107.tck.testutil.TestSupport;
import org.junit.After;
import org.junit.Before;

import javax.cache.Cache;
import javax.cache.configuration.MutableConfiguration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


/**
 * @author Jens Wilke
 */
public abstract class AdditionalTestSupport<K, V> extends TestSupport {

  private AutoCloseable builderResources;

  protected Cache<K, V> cache;

  @Before
  public void setUp() {
    MutableConfiguration<K, V> config = newMutableConfiguration();
    ConfigurationBuilder<K, V> builder = new ConfigurationBuilder<>(config);
    builderResources = builder;
    extraSetup(builder);
    cache = getCacheManager().createCache(getTestCacheName(), config);
  }

  @After
  public void tearDown() throws Exception {
    getCacheManager().destroyCache(getTestCacheName());
    builderResources.close();
  }

  abstract protected MutableConfiguration<K, V> newMutableConfiguration();

  protected void extraSetup(ConfigurationBuilder<K, V> builder) { }

  public static <T> Set<T> keys(T... keys) {
    return new HashSet<>(Arrays.asList(keys));
  }

}
