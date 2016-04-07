package org.cache2k;

/*
 * #%L
 * cache2k API only package
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

import org.cache2k.spi.SingleProviderResolver;
import org.cache2k.spi.StorageImplementation;
import org.cache2k.storage.SimpleSingleFileStorage;

import java.util.concurrent.TimeUnit;

/**
 * @author Jens Wilke; created: 2014-04-18
 */
public class StorageConfiguration<EX> {

  boolean reliable;

  boolean purgeOnStartup;

  boolean ignoreModifications;

  Class<? extends StorageImplementation> implementation = SimpleSingleFileStorage.class;

  boolean passivation = false;

  boolean readOnly = false;

  String location;

  String storageName;

  int entryCapacity = -1;

  int bytesCapacity;

  long syncInterval = 7 * 1000;

  EX extraConfiguration;

  boolean flushOnClose = false;

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
  public void setPurgeOnStartup(boolean purgeOnStartup) {
    this.purgeOnStartup = purgeOnStartup;
  }

  /**
   * @see Builder#purgeOnStartup
   */
  public boolean isPurgeOnStartup() {
    return purgeOnStartup;
  }

  public void setIgnoreModifications(boolean ignoreModifications) {
    this.ignoreModifications = ignoreModifications;
  }

  public void setImplementation(Class<? extends StorageImplementation> c) {
    implementation = c;
  }

  public void setPassivation(boolean passivation) {
    this.passivation = passivation;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  /**
   * Capacity limit for the number of entries. Default is -1, capacity is
   * limited by other means.
   */
  public void setEntryCapacity(int entryCapacity) {
    this.entryCapacity = entryCapacity;
  }

  public void setBytesCapacity(int bytesCapacity) {
    this.bytesCapacity = bytesCapacity;
  }

  public void setSyncInterval(long v, TimeUnit u) {
    this.syncInterval = u.toMillis(v);
  }

  public boolean isIgnoreModifications() {
    return ignoreModifications;
  }

  public Class<?> getImplementation() {
    return implementation;
  }

  public boolean isPassivation() {
    return passivation;
  }

  public String getLocation() {
    return location;
  }

  public int getEntryCapacity() {
    return entryCapacity;
  }

  public int getBytesCapacity() {
    return bytesCapacity;
  }

  /**
   * Sync interval in milliseconds.
   */
  public long getFlushIntervalMillis() {
    return syncInterval;
  }

  public EX getExtraConfiguration() {
    return extraConfiguration;
  }

  public void setExtraConfiguration(EX extraConfiguration) {
    this.extraConfiguration = extraConfiguration;
  }

  public boolean isFlushOnClose() {
    return flushOnClose;
  }

  /**
   * When closing flush data, if the storage does need this.
   * The parameter is false by default. We prefer no delay on closing.
   */
  public void setFlushOnClose(boolean f) {
    this.flushOnClose = f;
  }

  public String getStorageName() {
    return storageName;
  }

  public void setStorageName(String v) {
    storageName = v;
  }

  public boolean isReadOnly() {
    return readOnly;
  }

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
     */
    public Builder<K, T, OPT_EXTRA_CONFIG> passivation(boolean f) {
      config.passivation = f;
      return this;
    }

    /**
     * False means single storage errors may be ignored.
     * True will propagate errors and flush the contents on shutdown.
     */
    public Builder<K, T, OPT_EXTRA_CONFIG> reliable(boolean f) {
      config.reliable = f;
      return this;
    }

    public Builder<K, T, OPT_EXTRA_CONFIG> purgeOnStartup(boolean f) {
      config.purgeOnStartup = f;
      return this;
    }

    /**
     * Flush contents on close. The default is off.
     *
     * <p>All defaults will be towards a "cache alike" behavior, not a
     * reliable storage behavior.
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

    public Builder<K, T, OPT_EXTRA_CONFIG> location(String s) {
      config.location = s;
      return this;
    }

    public Builder<K, T, OPT_EXTRA_CONFIG> entryCapacity(int v) {
      config.entryCapacity = v;
      return this;
    }

    public Builder<K, T, OPT_EXTRA_CONFIG> bytesCapacity(int v) {
      config.bytesCapacity = v;
      return this;
    }

    public Builder<K, T, OPT_EXTRA_CONFIG> syncInterval(int v, TimeUnit u) {
      config.syncInterval = (int) u.toMillis(v);
      return this;
    }

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
