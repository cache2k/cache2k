package org.cache2k;

/*
 * #%L
 * cache2k API only package
 * %%
 * Copyright (C) 2000 - 2014 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import org.cache2k.spi.Cache2kCoreProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * For easy of use it is possible to create a cache the direct way with a builder.
 * This is the configuration builder functionality we need for the cache builder
 * as well.
 *
 * @param <B> builder type, e.g. {@link CacheBuilder}
 *
 * @author Jens Wilke; created: 2014-04-19

 */
@SuppressWarnings("unchecked")
abstract class RootAnyBuilder<B, T> extends BaseAnyBuilder<B, T, CacheConfig> {

  private List<BaseAnyBuilder> modules = Collections.emptyList();
  protected CacheConfig config;
  StorageConfiguration.Builder<B, T> persistence;

  public B backgroundRefresh(boolean f) {
    config.setBackgroundRefresh(f);
    return (B) this;
  }

  public B sharpExpiry(boolean f) {
    config.setSharpExpiry(f);
    return (B) this;
  }

  @Override
  public StorageConfiguration.Builder<B, T> persistence() {
    if (persistence == null) {
      persistence = addModule(new StorageConfiguration.Builder());
      persistence.implementation(Cache2kCoreProvider.get().getDefaultPersistenceStoreImplementation());
    }
    return persistence;
  }

  @Override
  public StorageConfiguration.Builder<B, T> addStore() {
    return
      addModule(new StorageConfiguration.Builder());
  }

  @Override
  public CacheConfig createConfiguration() {
    List<Object> _moduleConfiguration = new ArrayList<>();
    for (BaseAnyBuilder bb : modules) {
      _moduleConfiguration.add(bb.createConfiguration());
    }
    config.setModuleConfiguration(_moduleConfiguration);
    return config;
  }

  /**
   * Either builds the configuration object or a cache.
   */
  public abstract T build();

  @SuppressWarnings("unchecked")
  private <B extends BaseAnyBuilder> B addModule(B _moduleBuilder) {
    if (modules.size() == 0) {
      modules = new ArrayList<>();
    }
    modules.add(_moduleBuilder);
    _moduleBuilder.setRoot(this);
    return _moduleBuilder;
  }

}
