package org.jsr107.tck.integration;

import org.junit.Assert;
import org.junit.Test;

import javax.cache.Cache;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.fail;

/**
 * Functional Tests for the {@link CacheWriterClient} and {@link CacheWriterServer}
 * classes.
 *
 * @author Joe Fialli
 */
public class CacheWriterClientServerTest {

    /**
     * Ensure that entry can be written from the {@link CacheWriterClient} via
     * the {@link CacheWriterServer}.
     */
    @Test
    public void shouldWriteFromServerWithClient() {

        RecordingCacheWriter<String, String> recordingCacheWriter = new RecordingCacheWriter<>();

        CacheWriterServer<String, String> serverCacheWriter = new CacheWriterServer<>(10000,
                                                                  recordingCacheWriter);

        try {
            serverCacheWriter.open();

            CacheWriterClient<String, String> clientCacheWriter =
                new CacheWriterClient<>(serverCacheWriter.getInetAddress(), serverCacheWriter.getPort());
            Cache.Entry<String, String> entry = new Entry<>("hello", "gudday");
            clientCacheWriter.write(entry);
            String writtenValue = recordingCacheWriter.get("hello");

            Assert.assertThat(writtenValue, is(notNullValue()));
            Assert.assertThat(writtenValue, is("gudday"));
            Assert.assertThat(recordingCacheWriter.hasWritten("hello"), is(true));
        } catch (Exception e) {}
        finally {
            serverCacheWriter.close();
        }
    }

    /**
     * Ensure that exceptions thrown by an underlying cache Writer are re-thrown.
     */
    @Test
    public void shouldRethrowExceptions() {

        FailingCacheWriter<String, String> failingCacheWriter = new FailingCacheWriter<>();

        CacheWriterServer<String, String> serverCacheWriter = new CacheWriterServer<>(10000,
                                                                  failingCacheWriter);

        try {
            serverCacheWriter.open();

            CacheWriterClient<String, String> clientCacheWriter =
                new CacheWriterClient<>(serverCacheWriter.getInetAddress(), serverCacheWriter.getPort());

            Cache.Entry<String, String> entry = new Entry<>("hello", "gudday");

            clientCacheWriter.write(entry);

            fail("An UnsupportedOperationException should have been thrown");
        } catch (Exception e) {}
        finally {
            serverCacheWriter.close();
        }
    }

    /**
     * Ensure that <code>null</code> entries can be passed from the
     * {@link CacheWriterServer} back to the {@link CacheWriterClient}.
     */

    /*
     * @Test
     * public void shouldLoadNullEntriesFromServerWithClient() {
     *
     *   NullEntryCacheWriter<String, String> nullCacheWriter = new NullEntryCacheWriter<>();
     *
     *   CacheWriterServer<String, String> serverCacheWriter =
     *   new CacheWriterServer<String, String>(10000, nullCacheWriter);
     *
     *   try {
     *       serverCacheWriter.open();
     *
     *       CacheWriterClient<String, String> clientCacheWriter =
     *       new CacheWriterClient<>(serverCacheWriter.getInetAddress(), serverCacheWriter.getPort());
     *
     *       Cache.Entry<String, String> entry = new Entry<String,String>("hello", "gudday");
     *
     *       Cache.Entry<String, String> entry = clientCacheWriter.write(entry);
     *
     *       Assert.assertThat(entry, is(nullValue()));
     *   } catch (Exception e) {
     *
     *   } finally {
     *       serverCacheWriter.close();
     *   }
     * }
     */

    private static class Entry<K, V> implements Cache.Entry<K, V> {
        private K key;
        private V value;

        public Entry(K key, V value) {
            setKey(key);
            setValue(value);
        }

        @Override
        public K getKey() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public V getValue() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public <T> T unwrap(Class<T> clazz) {
            throw new UnsupportedOperationException("not implemented");
        }

        public void setKey(K key) {
            this.key = key;
        }

        public void setValue(V value) {
            this.value = value;
        }
    }
}
