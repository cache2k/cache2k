package org.cache2k.config;

/*
 * #%L
 * cache2k API
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

/**
 * Features need to properly implement {@code equals()} and {@code hashCode()}
 *
 * @author Jens Wilke
 */
public interface Feature {

  /**
   * The feature enables itself by augmenting the cache config.
   * Called when {@link org.cache2k.Cache2kBuilder#build} is called
   * before the actual cache creation.
   */
  void enlist(CacheBuildContext<?, ?> ctx);

}
