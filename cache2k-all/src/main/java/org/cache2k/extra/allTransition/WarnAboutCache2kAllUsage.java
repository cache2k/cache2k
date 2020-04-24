package org.cache2k.extra.allTransition;

/*
 * #%L
 * cache2k "all" transitional artifact
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

import org.cache2k.CacheManager;
import org.cache2k.core.spi.CacheManagerLifeCycleListener;
import org.cache2k.core.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wire in as cache2k extension service, so the warning pops up when cache2k is used.
 *
 * @author Jens Wilke
 */
public class WarnAboutCache2kAllUsage implements CacheManagerLifeCycleListener {

  private AtomicBoolean once = new AtomicBoolean(false);

  @Override
  public void managerCreated(final CacheManager m) {
    if (once.compareAndSet(false, true)) {
      Log.getLog(this.getClass()).warn(
        "Usage of the artifact cache2k-all is deprecated, please update your dependencies. " +
          "See: https://cache2k.org/docs/latest/user-guide.html#getting-started");
    }
  }

  @Override
  public void managerDestroyed(final CacheManager m) {

  }
}
