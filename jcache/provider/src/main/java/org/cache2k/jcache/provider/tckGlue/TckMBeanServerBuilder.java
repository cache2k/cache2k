package org.cache2k.jcache.provider.tckGlue;

/*
 * #%L
 * cache2k JCache JSR107 implementation
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
