package org.cache2k;

/*
 * #%L
 * cache2k implementation
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

import net.jcip.annotations.NotThreadSafe;
import org.cache2k.integration.CacheLoader;
import org.junit.After;
import org.junit.Test;

/**
 * @author Jens Wilke
 */
@NotThreadSafe
public class ReadThroughWithCompoundKeyExampleTest {

  Cache<Route, String> routeToAirline = new Cache2kBuilder<Route, String>() {}
    .name(this + "-routeToAirline")
    .eternal(true)
    .loader(new CacheLoader<Route, String>() {
      @Override
      public String load(final Route key) throws Exception {
        return findFavoriteAirline(key.getOrigin(), key.getDestination());
      }
    })
    .build();

  @After
  public void tearDown() {
    routeToAirline.close();
  }

  private String findFavoriteAirline(String origin, String destination) {
    return "People Air";
  }

  public String lookupFavoirteAirline(String origin, String destination) {
    return routeToAirline.get(new Route(origin, destination));
  }

  @Test
  public void test() {
    lookupFavoirteAirline("MUC", "JFK");
  }

  static final class Route {
    private String origin;
    private String destination;

    public Route(final String origin, final String destination) {
      this.destination = destination;
      this.origin = origin;
    }

    public String getOrigin() {
      return origin;
    }

    public String getDestination() {
      return destination;
    }

    @Override
    public boolean equals(final Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;
      Route __route = (Route) other;
      if (!origin.equals(__route.origin)) return false;
      return destination.equals(__route.destination);
    }

    @Override
    public int hashCode() {
      int hashCode = origin.hashCode();
      hashCode = 31 * hashCode + destination.hashCode();
      return hashCode;
    }
  }

}
