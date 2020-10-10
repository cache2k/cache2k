package org.cache2k.core.util;

/*
 * #%L
 * cache2k core implementation
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * A set of utility stuff we need often.
 *
 * @author Jens Wilke
 */
public class Util {

  public static final String NAME_SEPARATOR = ":";

  /**
   * Format milliseconds since epoch to a compact timestamp.
   */
  public static String formatMillis(long millis) {
    LocalDateTime t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime();
    return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(t);

  }

  public static String compactFullName(CacheManager mgr, String cacheName) {
    if (!CacheManager.STANDARD_DEFAULT_MANAGER_NAME.equals(mgr.getName())) {
      return mgr.getName() + NAME_SEPARATOR + cacheName;
    }
    return cacheName;
  }

}
