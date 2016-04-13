package org.cache2k.spi;

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

import org.cache2k.AnyBuilder;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;

/**
 * A builder that actually does build nothing. Used for submodules which have no
 * extra configuration.
 *
 * @author Jens Wilke; created: 2014-06-21
 */
public class VoidConfigBuilder<K, T> implements AnyBuilder<K, T, Void> {

  private Cache2kBuilder<K, T> root;

  public VoidConfigBuilder(Cache2kBuilder<K, T> root) {
    this.root = root;
  }

  @Override
  public Void createConfiguration() {
    return null;
  }

  @Override
  public Cache2kBuilder<K, T> root() {
    return root;
  }

  @Override
  public Cache<K, T> build() {
    return root.build();
  }

}
