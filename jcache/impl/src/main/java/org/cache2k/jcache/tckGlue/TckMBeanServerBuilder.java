package org.cache2k.jcache.tckGlue;

/*
 * #%L
 * cache2k JCache JSR107 implementation
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

import com.sun.jmx.mbeanserver.JmxMBeanServer;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerBuilder;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;

/**
 * This is actually a copy of the RI code, which is only needed for the TCK.
 *
 * @author Jens Wilke; created: 2015-04-29
 */
public class TckMBeanServerBuilder extends MBeanServerBuilder {

   /**
   * Empty public constructor as required
   */
  public TckMBeanServerBuilder() {
    super();
  }

  @Override
  public MBeanServer newMBeanServer(String defaultDomain, MBeanServer outer,
                                    javax.management.MBeanServerDelegate delegate) {
    javax.management.MBeanServerDelegate decoratingDelegate = new MBeanServerDelegate(delegate);
    return JmxMBeanServer.newMBeanServer(defaultDomain, outer,
        decoratingDelegate, false);
  }

  /**
   * A decorator around the MBeanServerDelegate which sets the mBeanServerId
   * to the value of the <code>org.jsr107.tck.management.agentId</code> system
   * property so that the TCK can precisely identify the correct MBeanServer
   * when running tests.
   */
  public class MBeanServerDelegate extends javax.management.MBeanServerDelegate {

    private javax.management.MBeanServerDelegate delegate;

    /**
     * Constructor
     *
     * @param delegate the provided delegate
     */
    public MBeanServerDelegate(javax.management.MBeanServerDelegate delegate) {
      this.delegate = delegate;
    }

    @Override
    public String getSpecificationName() {
      return delegate.getSpecificationName();
    }

    @Override
    public String getSpecificationVersion() {
      return delegate.getSpecificationVersion();
    }

    @Override
    public String getSpecificationVendor() {
      return delegate.getSpecificationVendor();
    }

    @Override
    public String getImplementationName() {
      return delegate.getImplementationName();
    }

    @Override
    public String getImplementationVersion() {
      return delegate.getImplementationVersion();
    }

    @Override
    public String getImplementationVendor() {
      return delegate.getImplementationVendor();
    }

    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
      return delegate.getNotificationInfo();
    }

    @Override
    public synchronized void addNotificationListener(NotificationListener listener,
                                                     NotificationFilter filter,
                                                     Object handback) throws
        IllegalArgumentException {
      delegate.addNotificationListener(listener, filter, handback);
    }

    @Override
    public synchronized void removeNotificationListener(NotificationListener
                                                                listener,
                                                        NotificationFilter
                                                            filter,
                                                        Object handback) throws
        ListenerNotFoundException {
      delegate.removeNotificationListener(listener, filter, handback);
    }

    @Override
    public synchronized void removeNotificationListener(NotificationListener
                                                                listener) throws
        ListenerNotFoundException {
      delegate.removeNotificationListener(listener);
    }

    @Override
    public void sendNotification(Notification notification) {
      delegate.sendNotification(notification);
    }

    @Override
    public synchronized String getMBeanServerId() {
      return System.getProperty("org.jsr107.tck.management.agentId");
    }
  }

}
