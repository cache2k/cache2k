package org.cache2k;

/*
 * #%L
 * cache2k core
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

import org.cache2k.integration.CacheLoader;
import org.junit.After;
import org.junit.Test;

/**
 * @author Jens Wilke
 */
public class ExpiryPolicyExampleTest {

  Cache<String, String> routeToAirline = new Cache2kBuilder<String, String>() {}
    .name(this + "-routeToAirline")
    .eternal(true)
    .loader(new CacheLoader<String, String>() {
      @Override
      public String load(final String key) throws Exception {
        String[] port = key.split("-");
        return findFavoriteAirline(port[0], port[1]);
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
    String route = origin + "-" + destination;
    return routeToAirline.get(route);
  }

  @Test
  public void test() {
    lookupFavoirteAirline("MUC", "JFK");
  }
}
