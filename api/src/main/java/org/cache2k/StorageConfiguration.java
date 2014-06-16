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

import java.util.concurrent.TimeUnit;

/**
 * @author Jens Wilke; created: 2014-04-18
 */
public class StorageConfiguration {

  boolean purgeOnStartup;

  boolean ignoreModifications;

  Class<?> implementation;

  boolean passivation = false;

  String location;

  int entryCapacity = -1;

  int bytesCapacity;

  long syncInterval = 7 * 1000;

  Object extendedConfiguration;

  public void setPurgeOnStartup(boolean purgeOnStartup) {
    this.purgeOnStartup = purgeOnStartup;
  }

  public void setIgnoreModifications(boolean ignoreModifications) {
    this.ignoreModifications = ignoreModifications;
  }

  public void setImplementation(Class<?> implementation) {
    this.implementation = implementation;
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
  public long getSyncInterval() {
    return syncInterval;
  }

  public Object getExtendedConfiguration() {
    return extendedConfiguration;
  }

  public void setExtendedConfiguration(Object extendedConfiguration) {
    this.extendedConfiguration = extendedConfiguration;
  }

  public static class Builder<T>
    extends BaseAnyBuilder<T, StorageConfiguration> {

    private StorageConfiguration config = new StorageConfiguration();
    private BaseAnyBuilder<?, ?> extendedConfigurationBuilder = null;

    public Builder<T> implementation(Class<?> _impl) {
      config.implementation = _impl;
      return this;
    }

    /**
     * Only store entries in the storage that don't live in the
     * memory any more. E.g. when an entry gets evicted it is
     * stored.
     */
    public Builder<T> passivation(boolean f) {
      config.passivation = f;
      return this;
    }

    public Builder<T> purgeOnStartup(boolean f) {
      config.purgeOnStartup = f;
      return this;
    }

    public Builder<T> location(String s) {
      config.location = s;
      return this;
    }

    public Builder<T> entryCapacity(int v) {
      config.entryCapacity = v;
      return this;
    }

    public Builder<T> bytesCapacity(int v) {
      config.bytesCapacity = v;
      return this;
    }

    public Builder<T> syncInterval(int v, TimeUnit u) {
      config.syncInterval = (int) u.toMillis(v);
      return this;
    }

    public <B extends BaseAnyBuilder<T, ?>> B extendedConfiguraton(Class<B> c) {
      try {
        B b = c.newInstance();
        b.setRoot(root);
        return b;
      } catch (Exception e) {
        throw new RuntimeException();
      }
    }

    public StorageConfiguration createConfiguration() {
      if (extendedConfigurationBuilder != null) {
        config.setExtendedConfiguration(
          extendedConfigurationBuilder.createConfiguration());
      }
      return config;
    }

  }

}
