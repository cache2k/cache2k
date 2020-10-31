package org.cache2k.extra.config.test;

/*
 * #%L
 * cache2k JCache provider
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

import org.cache2k.config.CacheBuildContext;
import org.cache2k.config.CustomizationSupplier;

import java.io.Serializable;

/**
 * @author Jens Wilke
 */
public class AnotherDummyListener<K, V> extends BaseDummyListener<K, V> {

  public static  class Supplier
    implements CustomizationSupplier<AnotherDummyListener>, Serializable {

    @Override
    public AnotherDummyListener supply(CacheBuildContext buildContext) {
      return new AnotherDummyListener();
    }

    private String xy;
    private String value1;

    public String getXy() {
      return xy;
    }

    public void setXy(String v) {
      xy = v;
    }

    public String getValue1() {
      return value1;
    }

    public void setValue1(String v) {
      value1 = v;
    }
  }

}
