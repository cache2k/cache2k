package test;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2014 headissue GmbH, Munich
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

import junit.framework.TestCase;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.integration.CacheLoader;

/**
 * Basic sanity checks and examples.
 *
 * @author Jens Wilke; created: 2013-12-17
 */
public class BasicCacheTest extends TestCase {

  public void testPeekAndPut() {
    Cache<String,String> c =
      Cache2kBuilder.of(String.class, String.class)
        .eternal(true)
        .build();
    String val = c.peek("something");
    c.put("something", "hello");
    val = c.get("something");
    c.close();
  }

  public void testGetWithSource() {
    CacheLoader<String,Integer> _lengthCountingSource = new CacheLoader<String, Integer>() {
      @Override
      public Integer load(String o) {
        return o.length();
      }
    };
    Cache<String,Integer> c =
      Cache2kBuilder.of(String.class, Integer.class)
        .loader(_lengthCountingSource)
        .eternal(true)
        .build();
    int v = c.get("hallo");
    v = c.get("long string");
    c.close();
  }

}
