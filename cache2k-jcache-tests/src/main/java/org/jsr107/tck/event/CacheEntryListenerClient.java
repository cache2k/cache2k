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

package org.jsr107.tck.event;

import org.jsr107.tck.support.CacheClient;
import org.jsr107.tck.support.Operation;
import org.jsr107.tck.support.Server;

import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListener;
import javax.cache.event.CacheEntryListenerException;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.util.concurrent.ExecutionException;

/**
 * A {@link javax.cache.event.CacheEntryListener} that delegates requests to a
 * {@link org.jsr107.tck.event.CacheEntryListenerServer}. Added to support testing TCK in a distributed
 * environment.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 * @author Joe Fialli
 */
public class CacheEntryListenerClient<K, V> extends CacheClient
  implements CacheEntryListener<K, V>,
  CacheEntryCreatedListener<K, V>, CacheEntryUpdatedListener<K, V>,
  CacheEntryRemovedListener<K, V>, CacheEntryExpiredListener<K, V> {

  private transient CacheEntryListenerServer<K,V> directServer;

  /**
   * Constructs a {@link CacheEntryListenerClient}.
   *
   * @param address the {@link java.net.InetAddress} on which to connect to the
   * {@link org.jsr107.tck.event.CacheEntryListenerServer}
   * @param port    the port to which to connect to the {@link org.jsr107.tck.event.CacheEntryListenerServer}
   */
  public CacheEntryListenerClient(InetAddress address, int port) {
    super(address, port);
  }

  @Override
  protected boolean checkDirectCallsPossible() {
    Server server = Server.lookupServerAtLocalMachine(port);
    if (server != null) {
      directServer = ((CacheEntryListenerServer) server);
      return true;
    }
    return false;
  }

  @Override
  public void onCreated(Iterable<CacheEntryEvent<? extends K, ? extends V>> cacheEntryEvents) throws CacheEntryListenerException {
    if (isDirectCallable()) {
      for (CacheEntryListener<K,V> l : directServer.getListeners()) {
        if (l instanceof CacheEntryCreatedListener) {
          ((CacheEntryCreatedListener<K,V>) l).onCreated(cacheEntryEvents);
        }
      }
      return;
    }
    for (CacheEntryEvent<? extends K, ? extends V> event : cacheEntryEvents) {
      getClient().invoke(new OnCacheEntryEventHandler<K, V>(event));
    }
  }

  @Override
  public void onExpired(Iterable<CacheEntryEvent<? extends K, ? extends V>> cacheEntryEvents) throws CacheEntryListenerException {
    if (isDirectCallable()) {
      for (CacheEntryListener<K,V> l : directServer.getListeners()) {
        if (l instanceof CacheEntryExpiredListener) {
          ((CacheEntryExpiredListener<K,V>) l).onExpired(cacheEntryEvents);
        }
      }
      return;
    }
    // since ExpiryEvents are processed asynchronously, this may cause issues.
    // the test do not currently delay waiting for asynchronous expiry events to complete processing.
    // not breaking anything now, so leaving in for time being.
    for (CacheEntryEvent<? extends K, ? extends V> event : cacheEntryEvents) {
      getClient().invoke(new OnCacheEntryEventHandler<K, V>(event));
    }
  }

  @Override
  public void onRemoved(Iterable<CacheEntryEvent<? extends K, ? extends V>> cacheEntryEvents) throws CacheEntryListenerException {
    if (isDirectCallable()) {
      for (CacheEntryListener<K,V> l : directServer.getListeners()) {
        if (l instanceof CacheEntryRemovedListener) {
          ((CacheEntryRemovedListener<K,V>) l).onRemoved(cacheEntryEvents);
        }
      }
      return;
    }
    for (CacheEntryEvent<? extends K, ? extends V> event : cacheEntryEvents) {
      getClient().invoke(new OnCacheEntryEventHandler<K, V>(event));
    }
  }

  @Override
  public void onUpdated(Iterable<CacheEntryEvent<? extends K, ? extends V>> cacheEntryEvents)
    throws CacheEntryListenerException {
    if (isDirectCallable()) {
      for (CacheEntryListener<K,V> l : directServer.getListeners()) {
        if (l instanceof CacheEntryUpdatedListener) {
          ((CacheEntryUpdatedListener<K,V>) l).onUpdated(cacheEntryEvents);
        }
      }
      return;
    }
    for (CacheEntryEvent<? extends K, ? extends V> event : cacheEntryEvents) {
      getClient().invoke(new OnCacheEntryEventHandler<K, V>(event));
    }
  }

  /**
   * Represent a CacheEntryEvent to dispatch to server.
   * @param <K>
   * @param <V>
   */
  private static class OnCacheEntryEventHandler<K, V> implements Operation<Object> {
    private CacheEntryEvent event;

    public OnCacheEntryEventHandler(CacheEntryEvent<? extends K, ? extends V> event) {
      this.event = event;
    }

    @Override
    public String getType() {
      return event.getEventType().name();
    }

    @Override
    public Object onInvoke(ObjectInputStream ois, ObjectOutputStream oos)
      throws IOException, ClassNotFoundException, ExecutionException {
      Object result = null;
      try {
        // serialize components of source since source is definitely not serializable.
        // use these two components to resolve source in server.
        oos.writeUTF(event.getSource().getName());
        oos.writeObject(event.getSource().getCacheManager().getURI());

        // Serialize rest of CacheEntryEvent
        oos.writeObject(event.getKey());
        oos.writeObject(event.getValue());

        // commented out since there is an issue with working
        // with these next 2 fields.
        // be sure to read these in TestCacheEntryEvent.readObject
        // when trying to reinstate them.
        /*
        oos.writeBoolean(event.isOldValueAvailable());
        if (event.isOldValueAvailable()) {
          Object oldValue = null;
          try {
            oldValue = event.getOldValue();
          } catch (Throwable t) {
            t.printStackTrace();
          }
          oos.writeObject(oldValue);
        }
        */
        result = ois.readObject();
      } catch (Throwable t) {
        t.printStackTrace();
      }
      /*
         original TCK code only throws exception when CacheEntryListenerException is detected
         this swallows the exception from the test IOError and UnsupportedOperationException.
         See test: CacheListenerTest.testBrokenCacheEntryListener() ;jw
       */
      if (result instanceof CacheEntryListenerException) {
        throw ((CacheEntryListenerException)result);
      }

      /* Also throw other exceptions ;jw */
      if (result instanceof Error) {
        throw (Error) result;
      }
      if (result instanceof RuntimeException) {
        throw (RuntimeException) result;
      }
      if (result instanceof Throwable) {
        // The listener methods don't have declared checked exceptions, so we never should get here.
        throw new RuntimeException("got checked exception, never happens since not declared on method", (Throwable) result);
      }

      // nothing to return.
      return null;
    }
  }
}
