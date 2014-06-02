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
 * @author Jens Wilke; created: 2014-04-19
 */
public abstract class RootAnyBuilder<T> extends BaseAnyBuilder<T, CacheConfig> {

  private static final List<BaseAnyBuilder> EMPTY = Collections.emptyList();

  private List<BaseAnyBuilder> modules = EMPTY;
  protected CacheConfig config;

  @Override @SuppressWarnings("unchecked")
  public StorageConfiguration.Builder<T> addPersistence() {
    StorageConfiguration.Builder<T> b = addModule(new StorageConfiguration.Builder());
    b.implementation(Cache2kCoreProvider.get().getDefaultPersistenceStoreImplementation());
    return b;
  }

  @Override @SuppressWarnings("unchecked")
  public StorageConfiguration.Builder<T> addStore() {
    return
      addModule(new StorageConfiguration.Builder());
  }

  @Override
  public CacheConfig createConfiguration() {
    for (BaseAnyBuilder bb : modules) {
      config.addModuleConfiguration(bb.createConfiguration());
    }
    return config;
  }

  /**
   * Either builds the configuration object or a cache.
   */
  public abstract T build();

  @SuppressWarnings("unchecked")
  private <B extends BaseAnyBuilder> B addModule(B _moduleBuilder) {
    if (modules == EMPTY) {
      modules = new ArrayList<>();
    }
    modules.add(_moduleBuilder);
    _moduleBuilder.setRoot(this);
    return _moduleBuilder;
  }

}
