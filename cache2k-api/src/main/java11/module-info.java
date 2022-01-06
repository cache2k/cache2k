/*-
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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
/**
 * @author Jens Wilke
 */
module org.cache2k.api {
  requires static kotlin.annotations.jvm;
  requires static jsr305;
  exports org.cache2k;
  exports org.cache2k.config;
  exports org.cache2k.event;
  exports org.cache2k.expiry;
  exports org.cache2k.io;
  exports org.cache2k.processor;
  exports org.cache2k.spi;
  exports org.cache2k.operation;
  exports org.cache2k.annotation;
  uses org.cache2k.spi.Cache2kCoreProvider;
}
