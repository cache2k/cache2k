package org.cache2k.jcache;

/*
 * #%L
 * cache2k JCache JSR107 implementation
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import org.cache2k.Cache;
import org.junit.Test;
import static org.junit.Assert.*;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;

/**
 * @author Jens Wilke; created: 2015-03-29
 */
public class CacheManagerTest {

  @Test
  public void testSameProvider() {
    CachingProvider p1 = Caching.getCachingProvider();
    CachingProvider p2 = Caching.getCachingProvider();
    assertTrue(p1 == p2);
  }

  @Test
  public void testSameCacheManager() {
    CachingProvider p = Caching.getCachingProvider();
    CacheManager cm1 = p.getCacheManager();
    CacheManager cm2 = p.getCacheManager();
    assertTrue(cm1 == cm2);
  }

}
