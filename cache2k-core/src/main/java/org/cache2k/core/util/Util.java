package org.cache2k.core.util;

/*-
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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

import org.cache2k.CacheException;
import org.cache2k.CacheManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * A set of utility stuff we need often.
 *
 * @author Jens Wilke
 */
public class Util {

  public static final String NAME_SEPARATOR = ":";

  public static String compactFullName(CacheManager mgr, String cacheName) {
    if (!CacheManager.STANDARD_DEFAULT_MANAGER_NAME.equals(mgr.getName())) {
      return mgr.getName() + NAME_SEPARATOR + cacheName;
    }
    return cacheName;
  }

  public static String formatTime(Instant t) {
    LocalDateTime ldt = t.atZone(ZoneId.systemDefault()).toLocalDateTime();
    return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(ldt);
  }

  public static <T> T resolveSingleProvider(Class<T> spiClass, Supplier<T> fallback) {
    Iterator<T> it = ServiceLoader.load(spiClass).iterator();
    if (it.hasNext()) {
      return it.next();
    }
    return fallback.get();
  }

  /**
   * Wait for an async task, making it synchronous again. Future version
   * will do more asynchronously.
   */
  public static void waitFor(CompletableFuture<Void> future) {
    if (future == null) { return; }
    try {
      future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      throw new CacheException(e);
    }
  }

}
