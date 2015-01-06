package org.cache2k.ee.impl;

/*
 * #%L
 * cache2k for enterprise environments
 * %%
 * Copyright (C) 2000 - 2015 headissue GmbH, Munich
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

import org.cache2k.CacheManager;
import org.cache2k.spi.Cache2kExtensionProvider;

import javax.naming.Context;
import javax.naming.InitialContext;

/**
 * @author Jens Wilke; created: 2014-10-10
 */
public class JndiDefaultNameProvider implements Cache2kExtensionProvider {

  @Override
  public void register() {
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
