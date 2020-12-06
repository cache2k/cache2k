package org.cache2k.annotation;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
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

import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierNickname;
import javax.annotation.meta.When;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated element can be {@code null} under some circumstance. We
 * consequently use the convention of no annotation means not nullable.
 * All API packages there for have the annotation {@link NonNullApi}
 *
 * <p>The annotation name {@code Nullable} is sufficient for static analysis
 * frameworks like NullAway. For additional compatibility with Kotlin
 * this is a nickname of the JSR305 annotation. For this to work the
 * JSR305 annotations do not need to be on the class path.
 *
 * <p>This approach is identical to the Spring framework.
 *
 * @author Jens Wilke
 * @see <a href="https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/lang/Nullable.html"/>
 * @see <a href="https://kotlinlang.org/docs/reference/java-interop.html#nullability-annotations"/>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER,
  ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE})
@Nonnull(when = When.MAYBE)
@TypeQualifierNickname
public @interface Nullable {
}
