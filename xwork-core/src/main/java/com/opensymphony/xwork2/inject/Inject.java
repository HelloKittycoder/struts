/**
 * Copyright (C) 2006 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.opensymphony.xwork2.inject;

import static com.opensymphony.xwork2.inject.Container.DEFAULT_NAME;

import static java.lang.annotation.ElementType.*;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 * <p>Annotates members and parameters which should have their value[s]
 * injected.
 *
 * @author crazybob@google.com (Bob Lee)
 */
@Target({METHOD, CONSTRUCTOR, FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface Inject {

  /**
   * Dependency name. Defaults to {@link Container#DEFAULT_NAME}.
   * 进行依赖注入的名称。如果不声明，这个名称会被设置为'defauult'
   */
  String value() default DEFAULT_NAME;

  /**
   * Whether or not injection is required. Applicable only to methods and
   * fields (not constructors or parameters).
   * 是否必须进行依赖注入，仅仅对于方法和属性有效（对构造函数或方法参数无效）
   */
  boolean required() default true;
}
