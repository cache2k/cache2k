package org.cache2k.spi;

/*
 * #%L
 * cache2k API
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

import java.util.Properties;

/**
 * Controls lifetime of different cache managers.
 *
 * @author Jens Wilke; created: 2015-03-26
 */
public interface Cache2kManagerProvider {

  void setDefaultManagerName(ClassLoader cl, String s);

  String getDefaultManagerName(ClassLoader cl);

  CacheManager getManager(ClassLoader cl, String _name);

  ClassLoader getDefaultClassLoader();

  void close();

  void close(ClassLoader l);

  void close(ClassLoader l, String _name);

}
