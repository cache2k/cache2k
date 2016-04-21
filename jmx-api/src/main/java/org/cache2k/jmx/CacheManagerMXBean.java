package org.cache2k.jmx;

/*
 * #%L
 * cache2k JMX API
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

/**
 * @author Jens Wilke; created: 2013-12-20
 */
@SuppressWarnings("unused")
public interface CacheManagerMXBean {

  /**
   * Combined health of all caches.
   *
   * @see org.cache2k.jmx.CacheInfoMXBean#getAlert()
   */
  int getAlert();

  /**
   * Clear all associated caches.
   */
  void clear();

  String getVersion();

  String getBuildNumber();

}
