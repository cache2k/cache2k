package org.cache2k.storage;

/*
 * #%L
 * cache2k core package
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
