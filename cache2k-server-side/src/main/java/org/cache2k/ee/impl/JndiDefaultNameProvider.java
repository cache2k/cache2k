package org.cache2k.ee.impl;

/*
 * #%L
 * cache2k server side
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

import org.cache2k.CacheManager;
import org.cache2k.spi.Cache2kExtensionProvider;

import javax.naming.Context;
import javax.naming.InitialContext;

/**
 * @author Jens Wilke; created: 2014-10-10
 */
public class JndiDefaultNameProvider implements Cache2kExtensionProvider {

  @Override
  public void registerCache2kExtension() {
    try {
      Context ctx = new InitialContext();
      ctx = (Context) ctx.lookup("java:comp/env");
      String _name =
        (String) ctx.lookup("org.cache2k.CacheManager.defaultName");
      if (_name != null) {
        CacheManager.setDefaultName(_name);
      }
    } catch (Exception ignore) {
    }
  }

}
