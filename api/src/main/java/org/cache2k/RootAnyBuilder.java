package org.cache2k;

/*
 * #%L
 * cache2k API only package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Jens Wilke; created: 2014-04-19
 */
@SuppressWarnings("unchecked")
public abstract class RootAnyBuilder<K, V>
  extends BaseAnyBuilder<K, V, CacheConfig> {

  private List<BaseAnyBuilder> modules = Collections.emptyList();
  protected CacheConfig config;
  StorageConfiguration.Builder<K, V, ?> persistence;

  /** Closed for extension */
  RootAnyBuilder() { }

  @Override
  public StorageConfiguration.Builder<K, V, ?> persistence() {
    if (persistence == null) {
      persistence = addModule(new StorageConfiguration.Builder());
    }
    return persistence;
  }

  @Override
  public CacheConfig createConfiguration() {
    if (config.getValueType() == null) {
      config.setValueType(Object.class);
    }
    if (config.getKeyType() == null) {
      config.setKeyType(Object.class);
    }
    List<Object> _moduleConfiguration = new ArrayList<Object>();
    for (BaseAnyBuilder bb : modules) {
      _moduleConfiguration.add(bb.createConfiguration());
    }
    config.setModuleConfiguration(_moduleConfiguration);
    return config;
  }

  @SuppressWarnings("unchecked")
  private <B extends BaseAnyBuilder> B addModule(B _moduleBuilder) {
    if (modules.isEmpty()) {
      modules = new ArrayList<BaseAnyBuilder>();
    }
    modules.add(_moduleBuilder);
    _moduleBuilder.setRoot(root);
    return _moduleBuilder;
  }

}
