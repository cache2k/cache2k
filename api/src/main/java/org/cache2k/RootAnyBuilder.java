package org.cache2k;

/*
 * #%L
 * cache2k API only package
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Jens Wilke; created: 2014-04-19
 */
@SuppressWarnings("unchecked")
public abstract class RootAnyBuilder<K, T>
  extends BaseAnyBuilder<K, T, CacheConfig> {

  private List<BaseAnyBuilder> modules = Collections.emptyList();
  protected CacheConfig config;
  StorageConfiguration.Builder<K, T, ?> persistence;

  /** Closed for extension */
  RootAnyBuilder() { }

  public CacheBuilder<K,T> backgroundRefresh(boolean f) {
    config.setBackgroundRefresh(f);
    return (CacheBuilder<K,T>) this;
  }

  public CacheBuilder<K,T> sharpExpiry(boolean f) {
    config.setSharpExpiry(f);
    return (CacheBuilder<K,T>) this;
  }

  @Override
  public StorageConfiguration.Builder<K, T, ?> persistence() {
    if (persistence == null) {
      persistence = addModule(new StorageConfiguration.Builder());
    }
    return persistence;
  }

  @Override
  public CacheConfig createConfiguration() {
    List<Object> _moduleConfiguration = new ArrayList<Object>();
    for (BaseAnyBuilder bb : modules) {
      _moduleConfiguration.add(bb.createConfiguration());
    }
    config.setModuleConfiguration(_moduleConfiguration);
    return config;
  }

  @SuppressWarnings("unchecked")
  private <B extends BaseAnyBuilder> B addModule(B _moduleBuilder) {
    if (modules.size() == 0) {
      modules = new ArrayList<BaseAnyBuilder>();
    }
    modules.add(_moduleBuilder);
    _moduleBuilder.setRoot(root);
    return _moduleBuilder;
  }

}
