package org.cache2k.jcache.provider.event;

/*-
 * #%L
 * cache2k JCache provider
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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

import org.cache2k.config.Cache2kConfig;

import javax.cache.configuration.CacheEntryListenerConfiguration;
import java.util.Collection;
import java.util.Collections;

/**
 * Handles registering event listeners and event dispatching according to the JCache spec.
 * See Impl.
 *
 * @author Jens Wilke
 */
public interface EventHandling<K, V> {

  EventHandling DISABLED = new EventHandling() {
    private static final String ERROR =
      "Registering a listener on an active cache not enabled, " +
      "enable with: supportOnlineListenerAttachment";

    @Override
    public boolean deregisterListener(CacheEntryListenerConfiguration cfg) {
      throw new UnsupportedOperationException(ERROR);
    }

    @Override
    public Collection<CacheEntryListenerConfiguration> getAllListenerConfigurations() {
      return Collections.emptyList();
    }

    @Override
    public void registerListener(CacheEntryListenerConfiguration cfg) {
      throw new UnsupportedOperationException(ERROR);
    }

    @Override
    public void addInternalListenersToCache2kConfiguration(Cache2kConfig cfg) {

    }
  };

  void registerListener(CacheEntryListenerConfiguration<K, V> cfg);

  @SuppressWarnings("UnusedReturnValue")
  boolean deregisterListener(CacheEntryListenerConfiguration<K, V> cfg);

  Collection<CacheEntryListenerConfiguration<K, V>> getAllListenerConfigurations();

  void addInternalListenersToCache2kConfiguration(Cache2kConfig<K, V> cfg);

}
