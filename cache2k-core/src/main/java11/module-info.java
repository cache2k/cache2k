/*
 * #%L
 * cache2k core implementation
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
import org.cache2k.impl.serverSide.JmxSupport;

/**
 * @author Jens Wilke
 */
module org.cache2k.core {
  requires org.cache2k.api;
  requires java.sql;
  requires java.naming;
  requires java.management;
  requires static java.logging;
  requires static org.slf4j;
  exports org.cache2k.core.api;
  uses org.cache2k.core.util.LogFactory;
  uses org.cache2k.core.spi.CacheConfigurationProvider;
  uses org.cache2k.core.spi.CacheLifeCycleListener;
  uses org.cache2k.core.spi.CacheManagerLifeCycleListener;
  uses org.cache2k.spi.Cache2kExtensionProvider;
  provides org.cache2k.spi.Cache2kCoreProvider with org.cache2k.core.Cache2kCoreProviderImpl;
  provides org.cache2k.core.spi.CacheConfigurationProvider with org.cache2k.impl.xmlConfiguration.CacheConfigurationProviderImpl;
  provides org.cache2k.core.spi.CacheLifeCycleListener with JmxSupport;
  provides org.cache2k.core.spi.CacheManagerLifeCycleListener with JmxSupport;
}
