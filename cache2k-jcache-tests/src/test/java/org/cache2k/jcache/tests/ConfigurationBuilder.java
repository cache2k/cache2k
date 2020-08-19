package org.cache2k.jcache.tests;

/*
 * #%L
 * cache2k JCache tests
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

import org.jsr107.tck.event.CacheEntryListenerClient;
import org.jsr107.tck.event.CacheEntryListenerServer;
import org.jsr107.tck.integration.CacheLoaderClient;
import org.jsr107.tck.integration.CacheLoaderServer;

import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Factory;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.CacheEntryListener;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.integration.CacheLoader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Jens Wilke
 */
@SuppressWarnings("unchecked")
public class ConfigurationBuilder<K, V> implements AutoCloseable {

  private MutableConfiguration<K, V> config;
  private List<AutoCloseable> closable = new ArrayList<>();
  private CacheEntryListenerServer synchronousListenerServer;
  private CacheEntryListenerServer asynchronousListenerServer;

  public ConfigurationBuilder(final MutableConfiguration<K, V> config) {
    this.config = config;
  }

  public ConfigurationBuilder<K, V> loader(CacheLoader<K, V> loader) {
    CacheLoaderServer<K, V> server = new CacheLoaderServer<>(0, loader);
    try {
      server.open();
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
    closable.add(server);
    CacheLoaderClient<K, V> client = new CacheLoaderClient<>(server.getInetAddress(), server.getPort());
    config.setCacheLoaderFactory(FactoryBuilder.factoryOf(client));
    return this;
  }

  public ConfigurationBuilder<K, V> expiry(Factory<? extends ExpiryPolicy> p) {
    config.setExpiryPolicyFactory(p);
    return this;
  }

  public ConfigurationBuilder<K, V> syncListener(CacheEntryListener<K, V> cacheEventListener) {
    return listener(cacheEventListener, true);
  }


  public ConfigurationBuilder<K, V> listener(CacheEntryListener<K, V> cacheEventListener, final boolean synchronous) {
    if (synchronous) {
      if (synchronousListenerServer == null) {
        synchronousListenerServer = buildListenerServer(true);
      }
      synchronousListenerServer.addCacheEventListener(cacheEventListener);
      return this;
    }
    if (asynchronousListenerServer == null) {
      asynchronousListenerServer = buildListenerServer(false);
    }
    asynchronousListenerServer.addCacheEventListener(cacheEventListener);
    return this;
  }

  private CacheEntryListenerServer buildListenerServer(final boolean synchronous) {
    CacheEntryListenerServer server =
      new CacheEntryListenerServer<>(0, Integer.class, String.class);
    try {
      server.open();
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
    final CacheEntryListenerClient<K, V> client =
      new CacheEntryListenerClient<>(server.getInetAddress(), server.getPort());
    config.addCacheEntryListenerConfiguration(new CacheEntryListenerConfiguration<K, V>() {
      @Override
      public Factory<CacheEntryListener<? super K, ? super V>> getCacheEntryListenerFactory() {
        return (Factory<CacheEntryListener<? super K, ? super V>>)
          (Object) FactoryBuilder.factoryOf(client);
      }

      @Override
      public boolean isOldValueRequired() {
        return true;
      }

      @Override
      public Factory<CacheEntryEventFilter<? super K, ? super V>> getCacheEntryEventFilterFactory() {
        return null;
      }

      @Override
      public boolean isSynchronous() {
        return synchronous;
      }
    });
    closable.add(server);
    return server;
  }

  @Override
  public void close() throws Exception {
    for (AutoCloseable obj : closable) {
      obj.close();
    }
  }

}
