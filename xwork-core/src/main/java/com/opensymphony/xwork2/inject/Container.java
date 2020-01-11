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

import java.io.Serializable;
import java.util.Set;

/**
 * XWork容器
 * Injects dependencies into constructors, methods and fields annotated with
 * {@link Inject}. Immutable.
 *
 * <p>When injecting a method or constructor, you can additionally annotate
 * its parameters with {@link Inject} and specify a dependency name. When a
 * parameter has no annotation, the container uses the name from the method or
 * constructor's {@link Inject} annotation respectively.
 *
 * <p>For example:
 *
 * <pre>
 *  class Foo {
 *
 *    // Inject the int constant named "i".
 *    &#64;Inject("i") int i;
 *
 *    // Inject the default implementation of Bar and the String constant
 *    // named "s".
 *    &#64;Inject Foo(Bar bar, @Inject("s") String s) {
 *      ...
 *    }
 *
 *    // Inject the default implementation of Baz and the Bob implementation
 *    // named "foo".
 *    &#64;Inject void initialize(Baz baz, @Inject("foo") Bob bob) {
 *      ...
 *    }
 *
 *    // Inject the default implementation of Tee.
 *    &#64;Inject void setTee(Tee tee) {
 *      ...
 *    }
 *  }
 * </pre>
 *
 * <p>To create and inject an instance of {@code Foo}:
 *
 * <pre>
 *  Container c = ...;
 *  Foo foo = c.inject(Foo.class);
 * </pre>
 *
 * @see ContainerBuilder
 * @author crazybob@google.com (Bob Lee)
 */
public interface Container extends Serializable {

  /**
   * Default dependency name.
   * 定义默认的对象获取标识
   */
  String DEFAULT_NAME = "default";

  /**
   * Injects dependencies into the fields and methods of an existing object.
   * 进行对象依赖注入的基本操作系欸口，作为参数的object将被XWork容器进行处理。
   * object内部声明有@Inject的字段和方法，都将被注入受到容器托管的对象，
   * 从而建立起依赖关系
   */
  void inject(Object o);

  /**
   * Creates and injects a new instance of type {@code implementation}.
   * 创建一个类的实例并进行对象依赖注入
   */
  <T> T inject(Class<T> implementation);

  /**
   * Gets an instance of the given dependency which was declared in
   * {@link com.opensymphony.xwork2.inject.ContainerBuilder}.
   * 根据type和name作为唯一标识，获取容器中的Java类的实例
   */
  <T> T getInstance(Class<T> type, String name);

  /**
   * Convenience method.&nbsp;Equivalent to {@code getInstance(type,
   * DEFAULT_NAME)}.
   * 根据type和默认的name（default）作为唯一标识，获取容器中的Java类的实例
   */
  <T> T getInstance(Class<T> type);
  
  /**
   * Gets a set of all registered names for the given type
   * 根据type获取与这个type所对应的容器中所有注册过的name
   * @param type The instance type
   * @return A set of registered names or empty set if no instances are registered for that type
   */
  Set<String> getInstanceNames(Class<?> type);

  /**
   * Sets the scope strategy for the current thread.
   * 设置当前线程的作用范围策略
   */
  void setScopeStrategy(Scope.Strategy scopeStrategy);

  /**
   * Removes the scope strategy for the current thread.
   * 删除当前线程的作用范围的策略
   */
  void removeScopeStrategy();
}
