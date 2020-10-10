package org.cache2k.jcache;

/*
 * #%L
 * cache2k JCache provider
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

import org.cache2k.Cache2kBuilder;
import org.cache2k.configuration.Cache2kConfiguration;

import javax.cache.configuration.Factory;
import javax.cache.configuration.MutableConfiguration;

/**
 * Extends the JCache mutable configuration with an additional cache2k configuration.
 *
 * @author Jens Wilke
 */
public final class ExtendedMutableConfiguration<K, V>
  extends MutableConfiguration<K, V> implements ExtendedConfiguration<K, V> {

  /**
   * The preferred way to construct a JCache based on a cache2k configuration.
   * It is not needed to set any original parameters from {@link MutableConfiguration}.
   * It is possible to set parameters defined by JCache, e.g.
   * {@link MutableConfiguration#setCacheLoaderFactory(Factory)}. In this case the
   * settings will be merged. See the documentation.
   *
   * @see <a href="https://cache2k.org/docs/latest/user-guide.html#jcache">User Guide - JCache</a>
   */
  public static <K, V> ExtendedMutableConfiguration<K, V> of(Cache2kBuilder<K, V> builder) {
    return of(builder.toConfiguration());
  }

  public static <K, V> ExtendedMutableConfiguration<K, V> of(
    Cache2kConfiguration<K, V> configuration) {
    ExtendedMutableConfiguration<K, V> cfg = new ExtendedMutableConfiguration<K, V>();
    cfg.cache2kConfiguration = configuration;
    return cfg;
  }

  private Cache2kConfiguration<K, V> cache2kConfiguration;

  public Cache2kConfiguration<K, V> getCache2kConfiguration() {
    return cache2kConfiguration;
  }

  public void setCache2kConfiguration(final Cache2kConfiguration<K, V> cache2kConfiguration) {
    this.cache2kConfiguration = cache2kConfiguration;
  }

}
