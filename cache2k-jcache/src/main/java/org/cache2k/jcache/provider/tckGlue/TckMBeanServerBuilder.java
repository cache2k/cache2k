package org.cache2k.jcache.provider.tckGlue;

/*
 * #%L
 * cache2k JCache provider
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

import javax.management.MBeanServer;
import javax.management.MBeanServerBuilder;
import javax.management.MBeanServerDelegate;

/**
 * A tricky MBean server builder which produces an mbean server
 * with id "TckMBeanServer" for any requested server. The platform
 * MBean server becomes available under this name, too, which allows
 * as to pass JCache TCK version 1.0 and hopefully TCK version 1.1.
 *
 * <p>Ideally this class is redundant for TCK version 1.1 onwards.
 *
 * @author Jens Wilke
 */
public class TckMBeanServerBuilder extends MBeanServerBuilder {

  @Override
  public javax.management.MBeanServerDelegate newMBeanServerDelegate() {
    return new WrapperMBeanServerDelegate();
  }

  @Override
  public MBeanServer newMBeanServer(String defaultDomain, MBeanServer outer,
                                    javax.management.MBeanServerDelegate delegate) {
    return super.newMBeanServer(defaultDomain, outer, delegate);
  }

  public class WrapperMBeanServerDelegate extends MBeanServerDelegate {

    public WrapperMBeanServerDelegate() {
    }

    public String getMBeanServerId() {
      return "TckMBeanServer";
    }

    public String getImplementationVersion() {
      return "1.0";
    }

    public String getImplementationVendor() {
      return "cache2k.org";
    }

  }

}
