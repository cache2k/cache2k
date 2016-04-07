package org.cache2k.storage;

/*
 * #%L
 * cache2k core package
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

import java.io.Serializable;

/**
 * @author Jens Wilke; created: 2014-04-19
 */
public interface MarshallerFactory {

  /**
   * The type the marshaller is specialized for
   */
  Class<?> getType();

  /**
   * The higher the more specialized.
   */
  int getPriority();

  /**
   * Create a marshaller that is suitable for the type.
   */
  Marshaller createMarshaller(Class<?> _type);

  /**
   * Used to create a marshaller in a configuration that
   * was used for marshalling. This way it is possible to
   * add new marshallers to the system without being unable
   * to read existing data.
   */
  Marshaller createMarshaller(Parameters c);

  public static class Parameters implements Serializable {

    private Class<?> payloadType;
    private Class<?> marshallerFactory;
    private Class<?> marshallerType;
    private Object marshallerConfiguration;

    public Parameters(Class<?> marshallerType) {
      this.marshallerType = marshallerType;
    }

    public Class<?> getPayloadType() {
      return payloadType;
    }

    public void setPayloadType(Class<?> payloadType) {
      this.payloadType = payloadType;
    }

    public Class<?> getMarshallerFactory() {
      return marshallerFactory;
    }

    public void setMarshallerFactory(Class<?> marshallerFactory) {
      this.marshallerFactory = marshallerFactory;
    }

    public Object getMarshallerConfiguration() {
      return marshallerConfiguration;
    }

    public void setMarshallerConfiguration(Object marshallerConfiguration) {
      this.marshallerConfiguration = marshallerConfiguration;
    }

    public Class<?> getMarshallerType() {
      return marshallerType;
    }

    public void setMarshallerType(Class<?> marshallerType) {
      this.marshallerType = marshallerType;
    }
  }

}
