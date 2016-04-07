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

/**
 * Marker interface for off heap or other(?) transient storage.
 * Changes general behaviour: passivation is enabled by default.
 * No heap entries will be stored on shutdown. On eviction, even
 * non-dirty entries will be sent to the storage if not contained
 * yet.
 *
 * @author Jens Wilke; created: 2014-06-11
 */
public interface TransientStorageClass {
}
