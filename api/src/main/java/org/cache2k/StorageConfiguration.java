package org.cache2k;

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

import org.cache2k.spi.SingleProviderResolver;
import org.cache2k.spi.StorageImplementation;
import org.cache2k.storage.SimpleSingleFileStorage;

import java.util.concurrent.TimeUnit;

/**
 * Configuration options for external storage (off-heap). The set of parameters is relevant
 * for most storage implementations, the actual interpretation and support may differ for
 * each implementation and documented at the implementation.
 *
 * @author Jens Wilke; created: 2014-04-18
 */
public class StorageConfiguration<EX> {

  public final static int DEFAULT_FLUSH_INTERVAL_SECONDS = 7;

  private boolean reliable;

  private boolean purgeOnStartup;

  private Class<? extends StorageImplementation> implementation = SimpleSingleFileStorage.class;

  private boolean passivation = false;

  private boolean readOnly = false;

  private String location;

  private String storageName;

  private int entryCapacity = -1;

  private int bytesCapacity;

  private long flushInterval = DEFAULT_FLUSH_INTERVAL_SECONDS * 1000;

  private EX extraConfiguration;

  private boolean flushOnClose = false;

  /**
   * @see Builder#reliable
   */
  public boolean isReliable() {
    return reliable;
  }

  /**
   * @see Builder#reliable
   */
  public void setReliable(boolean reliable) {
    this.reliable = reliable;
  }

  /**
   * @see Builder#purgeOnStartup
   */
  public boolean isPurgeOnStartup() {
    return purgeOnStartup;
  }

  /**
   * @see Builder#purgeOnStartup
   */
  public void setPurgeOnStartup(boolean purgeOnStartup) {
    this.purgeOnStartup = purgeOnStartup;
  }

  /**
   * @see Builder#implementation
   */
  public void setImplementation(Class<? extends StorageImplementation> c) {
    implementation = c;
  }

  /**
   * @see Builder#passivation
   */
  public boolean isPassivation() {
    return passivation;
  }

  /**
   * @see Builder#passivation
   */
  public void setPassivation(boolean passivation) {
    this.passivation = passivation;
  }

  /**
   * @see Builder#location(String)
   */
  public String getLocation() {
    return location;
  }

  /**
   * @see Builder#location(String)
   */
  public void setLocation(String location) {
    this.location = location;
  }

  /**
   * @see #entryCapacity
   */
  public int getEntryCapacity() {
    return entryCapacity;
  }

  /**
   * @see #entryCapacity
   */
  public void setEntryCapacity(int entryCapacity) {
    this.entryCapacity = entryCapacity;
  }

  /**
   * @see #bytesCapacity
   */
  public int getBytesCapacity() {
    return bytesCapacity;
  }

  /**
   * @see #bytesCapacity
   */
  public void setBytesCapacity(int bytesCapacity) {
    this.bytesCapacity = bytesCapacity;
  }

  /**
   * @see Builder#flushInterval(long, TimeUnit)
   * @deprecated Use {@link #setFlushInterval}
   */
  public void setSyncInterval(long v, TimeUnit u) {
    this.flushInterval = u.toMillis(v);
  }

  /**
   * @see Builder#flushInterval(long, TimeUnit)
   */
  public void setFlushInterval(long v, TimeUnit u) {
    this.flushInterval = u.toMillis(v);
  }

  /**
   * @see Builder#flushInterval(long, TimeUnit)
   */
  public void setFlushIntervalMillis(long v) { this.flushInterval = v; }

  /**
   * Same as sync interval in milliseconds.
   *
   * @see Builder#flushInterval(long, TimeUnit)
   * @deprecated Use {@link #getFlushIntervalMillis()}
   */
  public long getSyncIntervalMillis() {
    return flushInterval;
  }

  /**
   * Same as sync interval in milliseconds.
   *
   * @see Builder#flushInterval(long, TimeUnit)
   * @deprecated Use {@link #setFlushIntervalMillis(long)}
   */
  public void setSyncIntervalMillis(long v) {
    flushInterval = v;
  }

  /**
   * Same as sync interval in milliseconds.
   *
   * @see Builder#flushInterval(long, TimeUnit)
   */
  public long getFlushIntervalMillis() {
    return flushInterval;
  }

  /**
   * @see Builder#implementation
   */
  public Class<?> getImplementation() {
    return implementation;
  }

  /**
   * Extra configuration bean for storage implementation, optional.
   *
   * @see Builder#extra(Class)
   */
  public EX getExtraConfiguration() {
    return extraConfiguration;
  }

  /**
   * Extra configuration specific to the storage implementation.
   *
   * @see Builder#extra(Class)
   */
  public void setExtraConfiguration(EX extraConfiguration) {
    this.extraConfiguration = extraConfiguration;
  }

  /**
   * @see #flushOnClose
   */
  public boolean isFlushOnClose() {
    return flushOnClose;
  }

  /**
   * @see #flushOnClose
   */
  public void setFlushOnClose(boolean f) {
    this.flushOnClose = f;
  }

  /**
   * @see Builder#storageName(String)
   */
  public String getStorageName() {
    return storageName;
  }

  /**
   * @see Builder#storageName(String)
   */
  public void setStorageName(String v) {
    storageName = v;
  }

  /**
   * @see Builder#readOnly(boolean)
   */
  public boolean isReadOnly() {
    return readOnly;
  }

  /**
   * @see Builder#readOnly(boolean)
   */
  public void setReadOnly(boolean readOnly) {
    this.readOnly = readOnly;
  }

  public static class Builder<K, T, OPT_EXTRA_CONFIG>
    extends BaseAnyBuilder<K, T, StorageConfiguration> {

    private StorageConfiguration<Object> config = new StorageConfiguration();
    private AnyBuilder<K, T, ?> extraConfigurationBuilder = null;

    /**
     * Only store entries in the storage that don't live in the
     * memory any more. E.g. when an entry gets evicted it is
     * stored.
     *
     * @param f true to store entries evicted from the heap cache. Default: false
     */
    public Builder<K, T, OPT_EXTRA_CONFIG> passivation(boolean f) {
      config.passivation = f;
      return this;
    }

    /**
     * Optimizes the storage behavior towards minimal data loss reducing the
     * "temporary storage" semantics of a cache. This includes {@link #flushOnClose}
     * and enforces stricter error handling. If a storage error occurs, this
     * will be propagated to the application.
     *
     * <p>For data that can be recreated, keep off, which is the default.
     *
     * @param f true means optimize against minimal data loss, false means optimize towards
     *          robustness and tolerance. Default: false
     * @see #flushOnClose(boolean)
     */
    public Builder<K, T, OPT_EXTRA_CONFIG> reliable(boolean f) {
      config.reliable = f;
      return this;
    }

    /**
     * Remove expired and redundant data from the storage on startup.
     * FIXME: NOT YET IMPLEMENTED.
     */
    public Builder<K, T, OPT_EXTRA_CONFIG> purgeOnStartup(boolean f) {
      config.purgeOnStartup = f;
      return this;
    }

    /**
     * Flush contents on close. The default is off. An alternative
     * is {@link #reliable}.
     *
     * <p>Rational: The idea is, that all defaults will be towards a "cache alike" behavior,
     * not a reliable storage behavior. The storage implementation may chose
     * to be buffered and not always flush the data after the cache mutation.
     * The data is not written on close by default to optimize towards a fast shutdown
     * of the application, usually, cached data can be regenerated any time.
     *
     * @see #reliable(boolean)
     * @see #flushInterval(long, TimeUnit)
     * @see Cache#flush()
     */
    public Builder<K, T, OPT_EXTRA_CONFIG> flushOnClose(boolean f) {
      config.flushOnClose = f;
      return this;
    }

    /**
     * Switch storage to read only mode, e.g. to examine the contents.
     * This parameter is not supported by all storage implementations.
     */
    public Builder<K, T, OPT_EXTRA_CONFIG> readOnly(boolean f) {
      config.readOnly = f;
      return this;
    }

    /**
     * Coordinates where the storage data resides, depending on the storage implementation.
     * For file system based storage this is a directory. For database storage a JDBC URL.
     * Empty by default.
     */
    public Builder<K, T, OPT_EXTRA_CONFIG> location(String s) {
      config.location = s;
      return this;
    }

    /**
     * Capacity limit for the number of entries. Default is -1, capacity is
     * limited by other means.
     *
     * @see CacheBuilder#heapEntryCapacity(int)
     * @see CacheBuilder#entryCapacity(int)
     */
    public Builder<K, T, OPT_EXTRA_CONFIG> entryCapacity(int v) {
      config.entryCapacity = v;
      return this;
    }

    /**
     * Storage capacity limit in bytes. FIXME: NOT YET IMPLEMENTED.
     */
    public Builder<K, T, OPT_EXTRA_CONFIG> bytesCapacity(int v) {
      config.bytesCapacity = v;
      return this;
    }

    /**
     * Maximal time interval that data should be made persistent or flushed
     * to the connected storage. At least after the configured interval
     * an attempt is started to flush all unwritten data. The implementation may chose
     * a possibly randomized smaller delay value, or other strategies to fulfill
     * this guarantee best possible.
     */
    public Builder<K, T, OPT_EXTRA_CONFIG> flushInterval(long v, TimeUnit u) {
      config.flushInterval = u.toMillis(v);
      return this;
    }

    /**
     * @deprecated Use {@link #flushInterval(long, TimeUnit)}
     */
    public Builder<K, T, OPT_EXTRA_CONFIG> syncInterval(long v, TimeUnit u) {
      config.flushInterval = u.toMillis(v);
      return this;
    }

    /**
     * For file based data storage this is the file name prefix to be used for all files.
     * Storage implementation may interpret this differently, e.g. a database storage would
     * interpret this as the table name to use. By default the storage name is derived from the
     * cache manager name and cache name, and set to "<em>&lt;manager-name></em>:<em>&lt;cache-name></em></code>".
     *
     * @see CacheManager#getName()
     * @see Cache#getName()
     */
    public Builder<K, T, OPT_EXTRA_CONFIG> storageName(String s) {
      config.storageName = s;
      return this;
    }


    public <EXTRA_CONFIG_BUILDER extends AnyBuilder<K, T, ?>> Builder<K, T, EXTRA_CONFIG_BUILDER> implementation(
      Class<? extends StorageImplementation<EXTRA_CONFIG_BUILDER>> c) {
      StorageImplementation<EXTRA_CONFIG_BUILDER> imp = SingleProviderResolver.getInstance().resolve(c);
      config.setImplementation(c);
      extraConfigurationBuilder = imp.createConfigurationBuilder(root());
      return (Builder<K, T, EXTRA_CONFIG_BUILDER>) this;
    }

    public <EXTRA_CONFIG_BUILDER extends AnyBuilder<K, T, ?>> EXTRA_CONFIG_BUILDER extra(
      Class<? extends StorageImplementation<EXTRA_CONFIG_BUILDER>> c) {
      StorageImplementation<EXTRA_CONFIG_BUILDER> imp = SingleProviderResolver.getInstance().resolve(c);
      config.setImplementation(c);
      extraConfigurationBuilder = imp.createConfigurationBuilder(root());
      return (EXTRA_CONFIG_BUILDER) extraConfigurationBuilder;
    }

    public OPT_EXTRA_CONFIG extra() {
      if (extraConfigurationBuilder == null) {
        throw new IllegalArgumentException("storage implementation has no extra configuration");
      }
      return (OPT_EXTRA_CONFIG) extraConfigurationBuilder;
    }

    @Override
    public StorageConfiguration createConfiguration() {
      if (extraConfigurationBuilder != null) {
        config.setExtraConfiguration(
          extraConfigurationBuilder.createConfiguration());
      }
      return config;
    }

  }

}
