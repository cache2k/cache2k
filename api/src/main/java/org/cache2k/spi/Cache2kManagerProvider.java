package org.cache2k.spi;

/*
 * #%L
 * cache2k API only package
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

import org.cache2k.CacheManager;

import java.util.Properties;

/**
 * Controls lifetime of different cache managers.
 *
 * @author Jens Wilke; created: 2015-03-26
 */
public interface Cache2kManagerProvider {

  void setDefaultName(String s);

  String getDefaultName();

  CacheManager getManager(ClassLoader cl, String _name, Properties p);

  CacheManager getDefaultManager(Properties p);

  ClassLoader getDefaultClassLoader();

  void close(ClassLoader l);

  void close();

  void close(ClassLoader l, String _name);

}
