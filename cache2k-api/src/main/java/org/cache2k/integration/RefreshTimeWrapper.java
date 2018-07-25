package org.cache2k.integration;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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
 * Instruct the case to use a different refresh time than the current time
 * for a loaded value. Use {@link Loaders#wrapRefreshTime(Object, long)}.
 * Don't use directly.
 *
 * @author Jens Wilke
 */
public final class RefreshTimeWrapper<V> extends LoadDetail<V> {

  private final long refreshTime;

  /**
   * Use {@link Loaders#wrapRefreshTime(Object, long)}
   */
  public RefreshTimeWrapper(final Object value, final long refreshTime) {
    super(value);
    this.refreshTime = refreshTime;
  }

  public long getRefreshTime() {
    return refreshTime;
  }

}
