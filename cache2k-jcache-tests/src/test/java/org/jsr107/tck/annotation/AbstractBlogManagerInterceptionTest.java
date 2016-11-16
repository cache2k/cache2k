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
package org.jsr107.tck.annotation;

import domain.Blog;
import manager.BlogManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author Rick Hightower
 */
public abstract class AbstractBlogManagerInterceptionTest extends AbstractInterceptionTest {
  protected abstract BlogManager getBlogManager();

  @Before
  public void before() {
    this.getBlogManager().clearCache();
  }

  /**
   *
   */
  @Test
  public void test_AT_CacheResult() {
    String testBody = "" + System.currentTimeMillis();
    String testTitle = "title a";
    Blog blog = new Blog(testTitle, testBody);
    BlogManager blogManager = getBlogManager();
    blogManager.createEntry(blog);

    Blog entryCached = blogManager.getEntryCached(testTitle);
    assertEquals(entryCached.getBody(), testBody);

        /* clear from map, but not from cache */
    blogManager.clearEntry(testTitle);
    entryCached = blogManager.getEntryCached(testTitle);
    assertNotNull("Item should still be in the cache thus not null",
        entryCached);
    assertEquals(
        "Item should still be in the cache and the title should be the same as before",
        entryCached.getBody(), testBody);

  }


  /**
   *
   */
  @Test
  public void test_AT_CacheResult_UsingAt_CacheKeyParam() {
    String testBody = "" + System.currentTimeMillis();
    String testTitle = "title abc";
    Blog blog = new Blog(testTitle, testBody);
    BlogManager blogManager = getBlogManager();
    blogManager.createEntry(blog);

    Blog entryCached = blogManager.getEntryCached("asdf", testTitle, "adsfa");
    assertEquals(entryCached.getBody(), testBody);

        /* clear from map, but not from cache */
    blogManager.clearEntry(testTitle);
    entryCached = blogManager.getEntryCached(testTitle);
    assertNotNull("Item should still be in the cache thus not null",
        entryCached);
    assertEquals(
        "Item should still be in the cache and the title should be the same as before",
        entryCached.getBody(), testBody);

  }


  @Test
  public void test_AT_CacheRemoveEntry() {
    String testBody = "" + System.currentTimeMillis();
    String testTitle = "title b";
    Blog blog = new Blog(testTitle, testBody);
    BlogManager blogManager = getBlogManager();
    blogManager.createEntry(blog);

    Blog entryCached = blogManager.getEntryCached(testTitle);
    assertEquals(entryCached.getBody(), testBody);

        /* clear from cache using annotation @CacheRemove */
    blogManager.clearEntryFromCache(testTitle);

        /* clear from map, but not from cache */
    blogManager.clearEntry(testTitle);

    entryCached = blogManager.getEntryCached(testTitle);
    assertNull("Item should removed from the cache and the map",
        entryCached);


  }


  @Test
  public void test_AT_CacheRemoveAll() {
    String testBody = "" + System.currentTimeMillis();
    String testTitle = "title b";

    Blog blog = new Blog(testTitle, testBody);
    BlogManager blogManager = getBlogManager();
    blogManager.createEntry(blog);

    Blog entryCached = blogManager.getEntryCached(testTitle);
    assertEquals(entryCached.getBody(), testBody);

        /* clear from map, but not from cache */
    blogManager.clearEntry(testTitle);

        /* clear from cache using annotation @CacheRemoveAll */
    blogManager.clearCache();

    entryCached = blogManager.getEntryCached(testTitle);

    assertNull("Item should removed from the cache and the map",
        entryCached);


  }

}
