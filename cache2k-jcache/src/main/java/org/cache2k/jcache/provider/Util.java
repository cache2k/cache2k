package org.cache2k.jcache.provider;

/*-
 * #%L
 * cache2k JCache provider
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

/**
 * @author Jens Wilke
 */
public class Util {

  public static <T> T checkKey(T value) {
    return requireNonNull(value, "cache key");
  }

  public static <T> T checkValue(T value) {
    return requireNonNull(value, "cache value");
  }

  public static <T> T requireNonNull(T value, String parameter) {
    if (value == null) {
      throw new NullPointerException("null not allowed for: " + parameter);
    }
    return value;
  }

}
