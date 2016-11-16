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
import javax.cache.integration.CacheWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * A {@link CacheWriter} that delegates requests to a {@link CacheWriterServer}.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 * @author Brian Oliver
 * @author Joe Fialli
 */
public class CacheWriterClient<K, V> extends CacheClient implements CacheWriter<K, V> {

    /**
     * Constructs a {@link CacheWriterClient}.
     *
     * @param address the {@link InetAddress} on which to connect to the {@link CacheWriterServer}
     * @param port    the port to which to connect to the {@link CacheWriterServer}
     */
    public CacheWriterClient(InetAddress address, int port) {
        super(address, port);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(Cache.Entry<? extends K, ? extends V> entry) {
        getClient().invoke(new WriteOperation<>(entry));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeAll(Collection<Cache.Entry<? extends K, ? extends V>> entries) {
        getClient().invoke(new WriteAllOperation<>(entries));
    }

    @Override
    public void delete(Object key) {
        getClient().invoke(new DeleteOperation<K, V>((K)key));

    }

    @Override
    public void deleteAll(Collection<?> keys) {
        getClient().invoke(new DeleteAllOperation<K, V>((Collection<K>) keys));
    }

    /**
     * The {@link DeleteAllOperation} representing a {@link CacheWriter#deleteAll(java.util.Collection)}
     * request.
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     */
    private static class DeleteAllOperation<K, V> implements Operation<Map<K, V>> {

        /**
         * The keys to Delete.
         */
        private Collection<? extends K> keys;

        /**
         * Constructs a {@link DeleteAllOperation}.
         *
         * @param keys the keys to Delete
         */
        public DeleteAllOperation(Collection<? extends K> keys) {
            this.keys = keys;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getType() {
            return "deleteAll";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Map<K, V> onInvoke(ObjectInputStream ois, ObjectOutputStream oos)
                throws IOException, ClassNotFoundException, ExecutionException {

            // send the keys to Delete
            for (K key : keys) {
                oos.writeObject(key);
            }

            oos.writeObject(null);

            // check for remote exceptions
            Object result = ois.readObject();
            Collection<K> notDeletedKeys;

            if (result instanceof RuntimeException) {
                notDeletedKeys = (Collection<K>) ois.readObject();

                // Partial Success processsing
                // returned keys were not able to be deleted.  remove from original keys list.
                Iterator<? extends K> iter = keys.iterator();
                while (iter.hasNext()) {
                    if (!notDeletedKeys.contains(iter.next())) {
                        iter.remove();
                    }
                }

                throw(RuntimeException) result;
            } else {

                // if no exception then all keys were deleted, remove all these keys to record that they were deleted.
                keys.clear();

                return null;
            }
        }
    }


    /**
     * The {@link DeleteOperation} representing a {@link CacheWriter#delete(Object)}
     * request.
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     */
    private static class DeleteOperation<K, V> implements Operation<V> {

        /**
         * The key to Delete.
         */
        private K key;

        /**
         * Constructs a {@link DeleteOperation}.
         *
         * @param key the Key to Delete
         */
        public DeleteOperation(K key) {
            this.key = key;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getType() {
            return "delete";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public V onInvoke(ObjectInputStream ois, ObjectOutputStream oos) throws IOException, ClassNotFoundException {
            oos.writeObject(key);

            Object o = ois.readObject();

            if (o instanceof RuntimeException) {
                throw(RuntimeException) o;
            } else {
                return null;
            }
        }
    }


    /**
     * The {@link WriteAllOperation} representing a {@link Cache#putAll(java.util.Map)} )}
     * request.
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     */
    private static class WriteAllOperation<K, V> implements Operation<Map<K, V>> {

        /**
         * The entries to write.
         */
        private Collection<Cache.Entry<? extends K, ? extends V>> entries;

        /**
         * Constructs a {@link WriteAllOperation}.
         *
         * @param entries the entries to write
         */
        public WriteAllOperation(Collection<Cache.Entry<? extends K, ? extends V>> entries) {
            this.entries = entries;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getType() {
            return "writeAll";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Map<K, V> onInvoke(ObjectInputStream ois, ObjectOutputStream oos)
                throws IOException, ClassNotFoundException {

            // send the entries to write
            for (Cache.Entry<? extends K, ? extends V> entry : entries) {
                oos.writeObject(entry.getKey());
                oos.writeObject(entry.getValue());
            }

            oos.writeObject(null);
            Object o = ois.readObject();

            if (o instanceof RuntimeException) {

                // Partial Success processsing, read in keys that failed to be written
                HashSet<K> failedToWriteKeys = new HashSet<>();
                K key = (K) ois.readObject();
                while (key != null) {
                    failedToWriteKeys.add(key);
                    key = (K) ois.readObject();
                }

                Iterator<Cache.Entry<? extends K, ? extends V>> iter = entries.iterator();
                while (iter.hasNext()) {
                    if (!failedToWriteKeys.contains(iter.next().getKey())) {
                        iter.remove();
                    }
                }

                throw(RuntimeException) o;
            } else {

                entries.clear();

                return null;
            }
        }
    }


    /**
     * The {@link WriteOperation} representing a {@link CacheWriter#write(javax.cache.Cache.Entry)}
     * request.
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     */
    private static class WriteOperation<K, V> implements Operation<V> {

        /**
         * The key to load.
         */
        private Cache.Entry<? extends K, ? extends V> entry;

        /**
         * Constructs a {@link WriteOperation}.
         *
         * @param entry the entry to write
         */
        public WriteOperation(Cache.Entry<? extends K, ? extends V> entry) {
            this.entry = entry;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getType() {
            return "write";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public V onInvoke(ObjectInputStream ois, ObjectOutputStream oos) throws IOException, ClassNotFoundException {
            oos.writeObject(entry.getKey());
            oos.writeObject(entry.getValue());

            Object o = ois.readObject();

            if (o instanceof RuntimeException) {
                throw(RuntimeException) o;
            } else {
                return null;
            }
        }
    }
}
