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

import org.cache2k.customization.ValueWithExpiryTime;

/**
 * If object implements {@link ValueWithNextRefreshTime} then the next refresh time
 * is fetched from the object. If not, the default linger time is used by the cache.
 *
 * @author Jens Wilke; created: 2013-05-02
 * @deprecated Use {@link ValueWithExpiryTime#AUTO_EXPIRY}
 */
public final class AutoEntryRefreshController<T> implements RefreshController<T> {

  public static final AutoEntryRefreshController INSTANCE = new AutoEntryRefreshController();

  @Override
  public long calculateNextRefreshTime(
    T _oldObject,
    T _newObject,
    long _timeOfLastRefresh, long now) {
    if (_newObject instanceof ValueWithNextRefreshTime) {
      return ((ValueWithNextRefreshTime) _newObject).getNextRefreshTime();
    }
    return Long.MAX_VALUE;
  }

}
