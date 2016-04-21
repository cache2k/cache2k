package org.cache2k.jcache.provider.generic.storeByValueSimulation;

/*
 * #%L
 * cache2k JSR107 support
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
 * @author Jens Wilke
 */
public class RuntimeCopyTransformer extends CopyTransformer<Object>  {

  @Override
  public Object copy(Object obj) {
    if (obj == null) {
      return null;
    }
    if (SimpleObjectCopyFactory.isImmutable(obj.getClass())) {
      return obj;
    }
    if (obj instanceof Serializable) {
      return SerializableCopyTransformer.copySerializableObject(obj);
    }
    throw new IllegalArgumentException("Cannot determine copy / marshalling method for: " + obj.getClass().getName());
  }

}
