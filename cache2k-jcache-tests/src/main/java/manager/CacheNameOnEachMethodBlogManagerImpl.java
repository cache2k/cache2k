/**
 *  Copyright 2011-2013 Terracotta, Inc.
 *  Copyright 2011-2013 Oracle, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package manager;

import domain.Blog;

import javax.cache.annotation.CacheKey;
import javax.cache.annotation.CacheRemove;
import javax.cache.annotation.CacheRemoveAll;
import javax.cache.annotation.CacheResult;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Rick Hightower
 */
public class CacheNameOnEachMethodBlogManagerImpl implements BlogManager {

  /**
   *
   */
  private static Map<String, Blog> map = new HashMap<String, Blog>();

  /**
   *
   */
  @CacheResult(cacheName = "blogManager")
  public Blog getEntryCached(String title) {
    return map.get(title);
  }

  /**
   *
   */
  public Blog getEntryRaw(String title) {
    return map.get(title);
  }

  /**
   * (non-Javadoc)
   *
   * @see manager.BlogManager#clearEntryFromCache(java.lang.String)
   */
  @CacheRemove(cacheName = "blogManager")
  public void clearEntryFromCache(String title) {
  }

  /**
   * (non-Javadoc)
   *
   * @see manager.BlogManager#clearEntry(java.lang.String)
   */
  public void clearEntry(String title) {
    map.put(title, null);
  }

  /**
   * (non-Javadoc)
   *
   * @see manager.BlogManager#clearCache()
   */
  @CacheRemoveAll(cacheName = "blogManager")
  public void clearCache() {
  }

  /**
   * (non-Javadoc)
   *
   * @see manager.BlogManager#createEntry(domain.Blog)
   */
  public void createEntry(Blog blog) {
    map.put(blog.getTitle(), blog);
  }

  /**
   *
   */
  @CacheResult(cacheName = "blogManager")
  public Blog getEntryCached(String randomArg, @CacheKey String title, String randomArg2) {
    return map.get(title);
  }

}
