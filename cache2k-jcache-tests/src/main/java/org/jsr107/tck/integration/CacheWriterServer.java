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

import org.jsr107.tck.support.OperationHandler;
import org.jsr107.tck.support.Server;

import javax.cache.Cache;
import javax.cache.integration.CacheWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.HashSet;

/**
 * A {@link Server} that handles {@link CacheWriter} requests from a
 * {@link CacheWriterClient} and delegates them to an underlying {@link CacheWriter}.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 * @author Brian Oliver
 * @author Joe Fialli
 */
public class CacheWriterServer<K, V> extends Server {

    /**
     * The underlying {@link CacheWriter} that will be used to
     * load entries requested by the {@link CacheWriterClient}s.
     */
    private CacheWriter<K, V> cacheWriter;

    /**
     * Constructs an CacheWriterServer.
     *
     * @param port        the port on which to accept {@link CacheWriterClient} requests
     * @param cacheWriter (optional) the {@link CacheWriter} that will be used to handle
     *                    client requests
     */
    public CacheWriterServer(int port, CacheWriter<K, V> cacheWriter) {
        super(port);

        // establish the client-server operation handlers
        addOperationHandler(new WriteOperationHandler());
        addOperationHandler(new WriteAllOperationHandler());
        addOperationHandler(new DeleteOperationHandler());
        addOperationHandler(new DeleteAllOperationHandler());

        this.cacheWriter = cacheWriter;
    }

    /**
     * Set the {@link CacheWriter} the {@link CacheWriterServer} should use
     * from now on.
     *
     * @param cacheWriter the {@link CacheWriter}
     */
    public void setCacheWriter(CacheWriter<K, V> cacheWriter) {
        this.cacheWriter = cacheWriter;
    }

    /**
     * The {@link OperationHandler} for a {@link CacheWriter#deleteAll(java.util.Collection)}} operation.
     */
    public class DeleteAllOperationHandler implements OperationHandler {
        @Override
        public String getType() {
            return "deleteAll";
        }

        @Override
        public void onProcess(ObjectInputStream ois, ObjectOutputStream oos)
                throws IOException, ClassNotFoundException {

            if (cacheWriter == null) {
                throw new NullPointerException("The cacheWriter for the CacheWriterServer has not be set");
            } else {
                HashSet<K> keys = new HashSet<>();

                K key = (K) ois.readObject();
                while (key != null) {
                    keys.add(key);

                    key = (K) ois.readObject();
                }

                try {
                    cacheWriter.deleteAll(keys);
                } catch (Exception e) {
                    oos.writeObject(e);
                    oos.writeObject(keys);

                    return;
                }

                oos.writeObject(keys);
            }
        }
    }


    /**
     * The {@link OperationHandler} for a {@link CacheWriter#delete(Object)} operation.
     */
    public class DeleteOperationHandler implements OperationHandler {

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
        public void onProcess(ObjectInputStream ois, ObjectOutputStream oos)
                throws IOException, ClassNotFoundException {
            if (cacheWriter == null) {
                throw new NullPointerException("The cacheWriter for the CacheWriterServer has not be set");
            } else {

                K key = (K) ois.readObject();
                try {
                    cacheWriter.delete(key);
                } catch (Exception e) {
                    oos.writeObject(e);

                    return;
                }

                // successful completion without an exception.
                oos.writeObject(null);
            }
        }
    }


    /**
     * An implementation of Cache.Entry.
     * @param <K>
     * @param <V>
     */
    private static class Entry<K, V> implements Cache.Entry<K, V> {
        private final K key;
        private final V value;

        public Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public <T> T unwrap(Class<T> clazz) {
            throw new UnsupportedOperationException("not implemented");
        }
    }


    /**
     * The {@link OperationHandler} for a {@link CacheWriter#writeAll(java.util.Collection)}} operation.
     */
    public class WriteAllOperationHandler implements OperationHandler {
        @Override
        public String getType() {
            return "writeAll";
        }

        private Collection<Cache.Entry<? extends K, ? extends V>> readEntries(ObjectInputStream ois)
                throws IOException, ClassNotFoundException {
            Collection<Cache.Entry<? extends K, ? extends V>> entrys = new HashSet<Cache.Entry<? extends K,
                                                                           ? extends V>>();

            K key = (K) ois.readObject();
            V value = null;
            if (key != null) {
                value = (V) ois.readObject();
            }

            Entry entry = ((key == null) || (value == null))
                          ? null
                          : new Entry(key, value);
            while (entry != null) {
                entrys.add(entry);
                key = (K) ois.readObject();
                value = null;

                if (key != null) {
                    value = (V) ois.readObject();
                }

                entry = ((key == null) || (value == null))
                        ? null
                        : new Entry(key, value);
            }

            return entrys;
        }

        @Override
        public void onProcess(ObjectInputStream ois, ObjectOutputStream oos)
                throws IOException, ClassNotFoundException {
            if (cacheWriter == null) {
                throw new NullPointerException("The cacheWriter for the CacheWriterServer has not be set");
            } else {
                Collection<Cache.Entry<? extends K, ? extends V>> entrys = readEntries(ois);
                try {
                    cacheWriter.writeAll(entrys);
                } catch (Exception e) {
                    oos.writeObject(e);

                    for (Cache.Entry<? extends K, ? extends V> entry1 : entrys) {
                        oos.writeObject(entry1.getKey());
                    }

                    oos.writeObject(null);

                    return;
                }

                assert(entrys.size() == 0);
                oos.writeObject(null);
            }
        }
    }


    /**
     * The {@link OperationHandler} for a {@link CacheWriter#write(javax.cache.Cache.Entry)} operation.
     */
    public class WriteOperationHandler implements OperationHandler {

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
        public void onProcess(ObjectInputStream ois, ObjectOutputStream oos)
                throws IOException, ClassNotFoundException {
            if (cacheWriter == null) {
                throw new NullPointerException("The cacheWriter for the CacheWriterServer has not be set");
            } else {
                final K key = (K) ois.readObject();
                final V value = (V) ois.readObject();
                Cache.Entry<K, V> entry = new Entry<>(key, value);
                try {
                    if ((key != null) && (value != null)) {
                        cacheWriter.write(entry);
                    }
                } catch (Exception e) {
                    oos.writeObject(e);

                    return;
                }

                // successfully completed operation.
                oos.writeObject(null);
            }
        }
    }
}
