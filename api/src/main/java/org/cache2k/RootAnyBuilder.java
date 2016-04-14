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
public abstract class RootAnyBuilder<K, V>
  extends BaseAnyBuilder<K, V, CacheConfig> {

  private List<BaseAnyBuilder> modules = Collections.emptyList();
  protected CacheConfig<K,V> config;

  /** Closed for extension */
  RootAnyBuilder() { }


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
    if (modules.isEmpty()) {
      modules = new ArrayList<BaseAnyBuilder>();
    }
    modules.add(_moduleBuilder);
    _moduleBuilder.setRoot(root);
    return _moduleBuilder;
  }

}
