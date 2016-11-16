/**
 *  Copyright 2011-2013 Terracotta, Inc.
 *  Copyright 2011-2013 Oracle, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jsr107.tck.integration;

import org.jsr107.tck.support.CacheClient;
import org.jsr107.tck.support.Operation;

import javax.cache.Cache;
import javax.cache.integration.CacheLoader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * A {@link CacheLoader} that delegates requests to a {@link CacheLoaderServer}.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 * @author Brian Oliver
 */
public class CacheLoaderClient<K, V> extends CacheClient implements CacheLoader<K, V> {

  /**
   * Constructs a {@link CacheLoaderClient}.
   *
   * @param address the {@link InetAddress} on which to connect to the {@link CacheLoaderServer}
   * @param port    the port to which to connect to the {@link CacheLoaderServer}
   */
  public CacheLoaderClient(InetAddress address, int port) {
    super(address, port);

    this.client = null;
  }

  @Override
  public V load(final K key) {
    return getClient().invoke(new LoadOperation<K, V>(key));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<K, V> loadAll(Iterable<? extends K> keys) {
    return getClient().invoke(new LoadAllOperation<K, V>(keys));
  }

  /**
   * The {@link LoadOperation} representing a {@link CacheLoader#load(Object)}
   * request.
   *
   * @param <K> the type of keys
   * @param <V> the type of values
   */
  private static class LoadOperation<K, V> implements Operation<V> {
    /**
     * The key to load.
     */
    private K key;

    /**
     * Constructs a {@link LoadOperation}.
     *
     * @param key the Key to load
     */
    public LoadOperation(K key) {
      this.key = key;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getType() {
      return "load";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V onInvoke(ObjectInputStream ois,
                      ObjectOutputStream oos) throws IOException, ClassNotFoundException {
      oos.writeObject(key);

      Object o = ois.readObject();

      if (o instanceof RuntimeException) {
        throw (RuntimeException) o;
      } else {
        return (V) o;
      }
    }
  }

  /**
   * The {@link LoadAllOperation} representing a {@link Cache#loadAll(java.util.Set, boolean, javax.cache.integration.CompletionListener)}
   * request.
   *
   * @param <K> the type of keys
   * @param <V> the type of values
   */
  private static class LoadAllOperation<K, V> implements Operation<Map<K, V>> {
    /**
     * The keys to load.
     */
    private Iterable<? extends K> keys;

    /**
     * Constructs a {@link LoadAllOperation}.
     *
     * @param keys the keys to load
     */
    public LoadAllOperation(Iterable<? extends K> keys) {
      this.keys = keys;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getType() {
      return "loadAll";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<K, V> onInvoke(ObjectInputStream ois, ObjectOutputStream oos)
        throws IOException, ClassNotFoundException, ExecutionException {

      //send the keys to load
      for(K key : keys) {
        oos.writeObject(key);
      }
      oos.writeObject(null);

      //read the resulting map
      HashMap<K, V> map = new HashMap<K, V>();

      Object result = ois.readObject();
      while (result != null && !(result instanceof Exception)) {
        K key = (K)result;
        V value = (V) ois.readObject();

        map.put(key, value);

        result = ois.readObject();
      }

      if (result instanceof RuntimeException) {
        throw (RuntimeException)result;
      } else {
        return map;
      }
    }
  }
}
