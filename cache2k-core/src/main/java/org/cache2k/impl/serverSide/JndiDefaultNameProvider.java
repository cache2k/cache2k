package org.cache2k.impl.serverSide;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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
import org.cache2k.core.util.Log;
import org.cache2k.spi.Cache2kExtensionProvider;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NoInitialContextException;

/**
 * Set default name of the cache manager from the JNDI property {@code org.cache2k.CacheManager.defaultName}.
 * The intention is to avoid naming conflicts in case multiple applications using cache2k
 * are run within one Apache Tomcat.
 *
 * @author Jens Wilke
 */
public class JndiDefaultNameProvider implements Cache2kExtensionProvider {

  @Override
  public void registerCache2kExtension() {
    try {
      try {
        Context ctx = new InitialContext();
        ctx = (Context) ctx.lookup("java:comp/env");
        String _name =
          (String) ctx.lookup("org.cache2k.CacheManager.defaultName");
        if (_name != null) {
          CacheManager.setDefaultName(_name);
        }
      } catch (NoInitialContextException ignore) {
      } catch (NameNotFoundException ignore) {
      }
    } catch (NoClassDefFoundError ignore) {
    } catch (Exception ex) {
      Log.getLog(JndiDefaultNameProvider.class).warn("Exception setting default cache manager name", ex);
    }
  }

}
