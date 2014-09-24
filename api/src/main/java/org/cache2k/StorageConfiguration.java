package org.cache2k;

/*
 * #%L
 * cache2k API only package
 * %%
 * Copyright (C) 2000 - 2014 headissue GmbH, Munich
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
 * @author Jens Wilke; created: 2014-04-18
 */
public class StorageConfiguration<EX> {

  boolean reliable;

  boolean purgeOnStartup;

  boolean ignoreModifications;

  Class<? extends StorageImplementation> implementation = SimpleSingleFileStorage.class;

  boolean passivation = false;

  String location;

  int entryCapacity = -1;

  int bytesCapacity;

  long syncInterval = 7 * 1000;

  EX extraConfiguration;

  boolean flushOnClose = false;

  public boolean isReliable() {
    return reliable;
  }

  public void setReliable(boolean reliable) {
    this.reliable = reliable;
  }

  public void setPurgeOnStartup(boolean purgeOnStartup) {
    this.purgeOnStartup = purgeOnStartup;
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

  public boolean isPurgeOnStartup() {
    return purgeOnStartup;
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

  public static class Builder<R extends RootAnyBuilder<R, T>, T, OPT_EXTRA_CONFIG>
    extends BaseAnyBuilder<R, T, StorageConfiguration> {

    private StorageConfiguration<Object> config = new StorageConfiguration();
    private AnyBuilder<R, T, ?> extraConfigurationBuilder = null;

    /**
     * Only store entries in the storage that don't live in the
     * memory any more. E.g. when an entry gets evicted it is
     * stored.
     */
    public Builder<R, T, OPT_EXTRA_CONFIG> passivation(boolean f) {
      config.passivation = f;
      return this;
    }

    public Builder<R, T, OPT_EXTRA_CONFIG> purgeOnStartup(boolean f) {
      config.purgeOnStartup = f;
      return this;
    }

    public Builder<R, T, OPT_EXTRA_CONFIG> flushOnClose(boolean f) {
      config.flushOnClose = f;
      return this;
    }

    public Builder<R, T, OPT_EXTRA_CONFIG> location(String s) {
      config.location = s;
      return this;
    }

    public Builder<R, T, OPT_EXTRA_CONFIG> entryCapacity(int v) {
      config.entryCapacity = v;
      return this;
    }

    public Builder<R, T, OPT_EXTRA_CONFIG> bytesCapacity(int v) {
      config.bytesCapacity = v;
      return this;
    }

    public Builder<R, T, OPT_EXTRA_CONFIG> syncInterval(int v, TimeUnit u) {
      config.syncInterval = (int) u.toMillis(v);
      return this;
    }


    public <EXTRA_CONFIG_BUILDER extends AnyBuilder<R, T, ?>> Builder<R, T, EXTRA_CONFIG_BUILDER> implementation(
      Class<? extends StorageImplementation<EXTRA_CONFIG_BUILDER>> c) {
      StorageImplementation<EXTRA_CONFIG_BUILDER> imp = SingleProviderResolver.getInstance().resolve(c);
      config.setImplementation(c);
      extraConfigurationBuilder = imp.createConfigurationBuilder(root());
      return (Builder<R, T, EXTRA_CONFIG_BUILDER>) this;
    }

    public <EXTRA_CONFIG_BUILDER extends AnyBuilder<R, T, ?>> EXTRA_CONFIG_BUILDER extra(
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
