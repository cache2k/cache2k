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

import kotlin.annotations.jvm.MigrationStatus;
import kotlin.annotations.jvm.UnderMigration;

import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierDefault;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sets the convention to non null parameters and return values on a package level.
 * We basically use that everywhere since its a common convention and implied
 * by tools like NullAway. However, we need to formally define it in the API
 * so Kotlin can infer the nullability correctly.
 *
 * <p>This uses the {@code @TypeQualifierDefault} from JSR305, which is also recognized by
 * Kotlin. Unfortunately Kotlin does not support the {@code @DefaultQualifiers} annotation from
 * the checker framework. For best tooling support we annotate the packages with both
 * annotations. An alternative would be to annotate @NonNull instead of having the default
 * convention Non-null except locals (NNEL).
 *
 * @author Jens Wilke
 * @see <a href="https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/lang/Nullable.html"/>
 * @see <a href="https://kotlinlang.org/docs/reference/java-interop.html#nullability-annotations"/>
 * @see <a href="https://stackoverflow.com/questions/4963300/which-notnull-java-annotation-should-i-use"/>
 * @see <a href="https://youtrack.jetbrains.com/issue/IDEA-144920"/>
 * @see <a href="https://youtrack.jetbrains.com/issue/KT-21408"/>
 */
@Target(value = {ElementType.PACKAGE, ElementType.TYPE})
@Retention(value = RetentionPolicy.CLASS)
@Documented
@Nonnull
@TypeQualifierDefault(value = {ElementType.METHOD, ElementType.PARAMETER})
@UnderMigration(status = MigrationStatus.STRICT)
public @interface NonNullApi { }
