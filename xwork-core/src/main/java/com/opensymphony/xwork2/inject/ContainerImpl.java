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

import com.opensymphony.xwork2.inject.util.ReferenceCache;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.Map.Entry;
import java.security.AccessControlException;

/**
 * Default {@link Container} implementation.
 *
 * @author crazybob@google.com (Bob Lee)
 * @see ContainerBuilder
 */
class ContainerImpl implements Container {

	final Map<Key<?>, InternalFactory<?>> factories;
	final Map<Class<?>, Set<String>> factoryNamesByType;

	ContainerImpl( Map<Key<?>, InternalFactory<?>> factories ) {
		this.factories = factories;
		Map<Class<?>, Set<String>> map = new HashMap<Class<?>, Set<String>>();
		for ( Key<?> key : factories.keySet() ) {
			Set<String> names = map.get(key.getType());
			if (names == null) {
				names = new HashSet<String>();
				map.put(key.getType(), names);
			}
			names.add(key.getName());
		}

		for ( Entry<Class<?>, Set<String>> entry : map.entrySet() ) {
			entry.setValue(Collections.unmodifiableSet(entry.getValue()));
		}

		this.factoryNamesByType = Collections.unmodifiableMap(map);
	}

	@SuppressWarnings("unchecked")
	<T> InternalFactory<? extends T> getFactory( Key<T> key ) {
		return (InternalFactory<T>) factories.get(key);
	}

	/**
	 * Field and method injectors.
	 * 字段和方法的注入器的初始化
	 */
	final Map<Class<?>, List<Injector>> injectors =
			new ReferenceCache<Class<?>, List<Injector>>() { // 这里采用了一个缓存（调get方法时）
				@Override
				protected List<Injector> create( Class<?> key ) {
					List<Injector> injectors = new ArrayList<Injector>();
					// 将当前的class类作为key，查找所有满足条件的Injector
					addInjectors(key, injectors);
					return injectors;
				}
			};

	/**
	 * 根据某个class，查找所有满足条件的注入器
	 * Recursively adds injectors for fields and methods from the given class to the given list. Injects parent classes
	 * before sub classes.
	 */
	void addInjectors( Class clazz, List<Injector> injectors ) {
		if (clazz == Object.class) {
			return;
		}

		// Add injectors for superclass first.
		// 首先递归调用自身，以完成对父类的注入器查找
		addInjectors(clazz.getSuperclass(), injectors);

		// TODO (crazybob): Filter out overridden members.
		// 针对所有属性查找满足条件的注入器，并加入到injectors中进行缓存
		addInjectorsForFields(clazz.getDeclaredFields(), false, injectors);
		// 针对所有方法查找满足条件的注入器，并加入到injectors中进行缓存
		addInjectorsForMethods(clazz.getDeclaredMethods(), false, injectors);
	}

	void injectStatics( List<Class<?>> staticInjections ) {
		final List<Injector> injectors = new ArrayList<Injector>();

		for ( Class<?> clazz : staticInjections ) {
			addInjectorsForFields(clazz.getDeclaredFields(), true, injectors);
			addInjectorsForMethods(clazz.getDeclaredMethods(), true, injectors);
		}

		callInContext(new ContextualCallable<Void>() {
			public Void call( InternalContext context ) {
				for ( Injector injector : injectors ) {
					injector.inject(context, null);
				}
				return null;
			}
		});
	}

	// 针对所有方法查找满足条件的注入器，并加入到injectors中进行缓存
	void addInjectorsForMethods( Method[] methods, boolean statics,
								 List<Injector> injectors ) {
		// 使用统一的接口进行查找，并在内部实现InjectorFactory，指定相应的Injector的实际实现类
		addInjectorsForMembers(Arrays.asList(methods), statics, injectors,
				new InjectorFactory<Method>() {
					public Injector create( ContainerImpl container, Method method,
											String name ) throws MissingDependencyException {
						// 这里指定MethodInjector作为Injector实现
						return new MethodInjector(container, method, name);
					}
				});
	}

	// 针对所有属性查找满足条件的注入器，并加入到injectors中进行缓存
	void addInjectorsForFields( Field[] fields, boolean statics,
								List<Injector> injectors ) {
		// 使用统一的接口进行查找，并在内部实现InjectorFactory，指定相应的Injector的实际实现类
		addInjectorsForMembers(Arrays.asList(fields), statics, injectors,
				new InjectorFactory<Field>() {
					public Injector create( ContainerImpl container, Field field,
											String name ) throws MissingDependencyException {
						// 这里指定FieldInjector作为Injector实现
						return new FieldInjector(container, field, name);
					}
				});
	}

	// 统一的Injector查找方式
	<M extends Member & AnnotatedElement> void addInjectorsForMembers(
			List<M> members, boolean statics, List<Injector> injectors,
			InjectorFactory<M> injectorFactory ) {
		for ( M member : members ) {
			if (isStatic(member) == statics) {
				// 查找当前传入的member是否具有@Inject的tAnnotation
				Inject inject = member.getAnnotation(Inject.class);
				if (inject != null) {
					try {
						// 调用传入的injectorFactory中的create方法创建真正的Injector实例
						injectors.add(injectorFactory.create(this, member, inject.value()));
					} catch ( MissingDependencyException e ) {
						if (inject.required()) {
							throw new DependencyException(e);
						}
					}
				}
			}
		}
	}

	interface InjectorFactory<M extends Member & AnnotatedElement> {

		Injector create( ContainerImpl container, M member, String name )
				throws MissingDependencyException;
	}

	private boolean isStatic( Member member ) {
		return Modifier.isStatic(member.getModifiers());
	}

	static class FieldInjector implements Injector {

		final Field field;
		final InternalFactory<?> factory;
		final ExternalContext<?> externalContext;

		// 构造函数，为实施依赖注入做数据准备
		public FieldInjector( ContainerImpl container, Field field, String name )
				throws MissingDependencyException {
			// 缓存field
			this.field = field;
			// 检查field是否可写，并设置field的可写属性
			if (!field.isAccessible()) {
				SecurityManager sm = System.getSecurityManager();
				try {
					if (sm != null) {
						sm.checkPermission(new ReflectPermission("suppressAccessChecks"));
					}
					field.setAccessible(true);
				} catch ( AccessControlException e ) {
					throw new DependencyException("Security manager in use, could not access field: "
							+ field.getDeclaringClass().getName() + "(" + field.getName() + ")", e);
				}
			}

			// 根据type和name到container内部工厂查找对应的对象构造工厂
			Key<?> key = Key.newInstance(field.getType(), name);
			factory = container.getFactory(key);
			// 如果没有找到相应的对象构造工厂，则注入失败
			if (factory == null) {
				throw new MissingDependencyException(
						"No mapping found for dependency " + key + " in " + field + ".");
			}

			// 为对象构建设置externalContext
			this.externalContext = ExternalContext.newInstance(field, key, container);
		}

		// 实际实施依赖注入的方法
		public void inject( InternalContext context, Object o ) {
			ExternalContext<?> previous = context.getExternalContext();
			context.setExternalContext(externalContext);
			try {
				// 使用初始化时找到的对象构造工厂创建对象，并使用反射进行注入
				field.set(o, factory.create(context));
			} catch ( IllegalAccessException e ) {
				throw new AssertionError(e);
			} finally {
				context.setExternalContext(previous);
			}
		}
	}

	/**
	 * Gets parameter injectors.
	 *
	 * @param member		 to which the parameters belong
	 * @param annotations	on the parameters
	 * @param parameterTypes parameter types
	 *
	 * @return injections
	 */
	<M extends AccessibleObject & Member> ParameterInjector<?>[]
	getParametersInjectors( M member,
							Annotation[][] annotations, Class[] parameterTypes, String defaultName )
			throws MissingDependencyException {
		List<ParameterInjector<?>> parameterInjectors =
				new ArrayList<ParameterInjector<?>>();

		Iterator<Annotation[]> annotationsIterator =
				Arrays.asList(annotations).iterator();
		for ( Class<?> parameterType : parameterTypes ) {
			Inject annotation = findInject(annotationsIterator.next());
			String name = annotation == null ? defaultName : annotation.value();
			Key<?> key = Key.newInstance(parameterType, name);
			parameterInjectors.add(createParameterInjector(key, member));
		}

		return toArray(parameterInjectors);
	}

	<T> ParameterInjector<T> createParameterInjector(
			Key<T> key, Member member ) throws MissingDependencyException {
		InternalFactory<? extends T> factory = getFactory(key);
		if (factory == null) {
			throw new MissingDependencyException(
					"No mapping found for dependency " + key + " in " + member + ".");
		}

		ExternalContext<T> externalContext =
				ExternalContext.newInstance(member, key, this);
		return new ParameterInjector<T>(externalContext, factory);
	}

	@SuppressWarnings("unchecked")
	private ParameterInjector<?>[] toArray(
			List<ParameterInjector<?>> parameterInjections ) {
		return parameterInjections.toArray(
				new ParameterInjector[parameterInjections.size()]);
	}

	/**
	 * Finds the {@link Inject} annotation in an array of annotations.
	 */
	Inject findInject( Annotation[] annotations ) {
		for ( Annotation annotation : annotations ) {
			if (annotation.annotationType() == Inject.class) {
				return Inject.class.cast(annotation);
			}
		}
		return null;
	}

	static class MethodInjector implements Injector {

		final Method method;
		final ParameterInjector<?>[] parameterInjectors;

		// 构造函数，为实施依赖注入做数据准备
		public MethodInjector( ContainerImpl container, Method method, String name )
				throws MissingDependencyException {
			// 缓存method
			this.method = method;
			// 检查method是否可写，并设置method的可写属性
			if (!method.isAccessible()) {
				SecurityManager sm = System.getSecurityManager();
				try {
					if (sm != null) {
						sm.checkPermission(new ReflectPermission("suppressAccessChecks"));
					}
					method.setAccessible(true);
				} catch ( AccessControlException e ) {
					throw new DependencyException("Security manager in use, could not access method: "
							+ name + "(" + method.getName() + ")", e);
				}
			}

			// 利用反射查找方法的每一个参数
			Class<?>[] parameterTypes = method.getParameterTypes();
			if (parameterTypes.length == 0) {
				throw new DependencyException(
						method + " has no parameters to inject.");
			}
			// 针对每个参数查找对应的Injector
			parameterInjectors = container.getParametersInjectors(
					method, method.getParameterAnnotations(), parameterTypes, name);
		}

		// 实际实施依赖注入的方法
		public void inject( InternalContext context, Object o ) {
			try {
				// 调用反射完成方法的调用，实施依赖注入
				method.invoke(o, getParameters(method, context, parameterInjectors));
			} catch ( Exception e ) {
				throw new RuntimeException(e);
			}
		}
	}

	Map<Class<?>, ConstructorInjector> constructors =
			new ReferenceCache<Class<?>, ConstructorInjector>() {
				@Override
				@SuppressWarnings("unchecked")
				protected ConstructorInjector<?> create( Class<?> implementation ) {
					return new ConstructorInjector(ContainerImpl.this, implementation);
				}
			};

	static class ConstructorInjector<T> {

		final Class<T> implementation;
		final List<Injector> injectors;
		final Constructor<T> constructor;
		final ParameterInjector<?>[] parameterInjectors;

		ConstructorInjector( ContainerImpl container, Class<T> implementation ) {
			this.implementation = implementation;

			constructor = findConstructorIn(implementation);
			if (!constructor.isAccessible()) {
				SecurityManager sm = System.getSecurityManager();
				try {
					if (sm != null) {
						sm.checkPermission(new ReflectPermission("suppressAccessChecks"));
					}
					constructor.setAccessible(true);
				} catch ( AccessControlException e ) {
					throw new DependencyException("Security manager in use, could not access constructor: "
							+ implementation.getName() + "(" + constructor.getName() + ")", e);
				}
			}

			MissingDependencyException exception = null;
			Inject inject = null;
			ParameterInjector<?>[] parameters = null;

			try {
				inject = constructor.getAnnotation(Inject.class);
				parameters = constructParameterInjector(inject, container, constructor);
			} catch ( MissingDependencyException e ) {
				exception = e;
			}
			parameterInjectors = parameters;

			if (exception != null) {
				if (inject != null && inject.required()) {
					throw new DependencyException(exception);
				}
			}
			injectors = container.injectors.get(implementation);
		}

		ParameterInjector<?>[] constructParameterInjector(
				Inject inject, ContainerImpl container, Constructor<T> constructor ) throws MissingDependencyException {
			return constructor.getParameterTypes().length == 0
					? null // default constructor.
					: container.getParametersInjectors(
					constructor,
					constructor.getParameterAnnotations(),
					constructor.getParameterTypes(),
					inject.value()
			);
		}

		@SuppressWarnings("unchecked")
		private Constructor<T> findConstructorIn( Class<T> implementation ) {
			Constructor<T> found = null;
			Constructor<T>[] declaredConstructors = (Constructor<T>[]) implementation
					.getDeclaredConstructors();
			for ( Constructor<T> constructor : declaredConstructors ) {
				if (constructor.getAnnotation(Inject.class) != null) {
					if (found != null) {
						throw new DependencyException("More than one constructor annotated"
								+ " with @Inject found in " + implementation + ".");
					}
					found = constructor;
				}
			}
			if (found != null) {
				return found;
			}

			// If no annotated constructor is found, look for a no-arg constructor
			// instead.
			try {
				return implementation.getDeclaredConstructor();
			} catch ( NoSuchMethodException e ) {
				throw new DependencyException("Could not find a suitable constructor"
						+ " in " + implementation.getName() + ".");
			}
		}

		/**
		 * Construct an instance. Returns {@code Object} instead of {@code T} because it may return a proxy.
		 */
		Object construct( InternalContext context, Class<? super T> expectedType ) {
			ConstructionContext<T> constructionContext =
					context.getConstructionContext(this);

			// We have a circular reference between constructors. Return a proxy.
			if (constructionContext.isConstructing()) {
				// TODO (crazybob): if we can't proxy this object, can we proxy the
				// other object?
				return constructionContext.createProxy(expectedType);
			}

			// If we're re-entering this factory while injecting fields or methods,
			// return the same instance. This prevents infinite loops.
			T t = constructionContext.getCurrentReference();
			if (t != null) {
				return t;
			}

			try {
				// First time through...
				constructionContext.startConstruction();
				try {
					Object[] parameters =
							getParameters(constructor, context, parameterInjectors);
					t = constructor.newInstance(parameters);
					constructionContext.setProxyDelegates(t);
				} finally {
					constructionContext.finishConstruction();
				}

				// Store reference. If an injector re-enters this factory, they'll
				// get the same reference.
				constructionContext.setCurrentReference(t);

				// Inject fields and methods.
				for ( Injector injector : injectors ) {
					injector.inject(context, t);
				}

				return t;
			} catch ( InstantiationException e ) {
				throw new RuntimeException(e);
			} catch ( IllegalAccessException e ) {
				throw new RuntimeException(e);
			} catch ( InvocationTargetException e ) {
				throw new RuntimeException(e);
			} finally {
				constructionContext.removeCurrentReference();
			}
		}
	}

	static class ParameterInjector<T> {

		final ExternalContext<T> externalContext;
		final InternalFactory<? extends T> factory;

		public ParameterInjector( ExternalContext<T> externalContext,
								  InternalFactory<? extends T> factory ) {
			this.externalContext = externalContext;
			this.factory = factory;
		}

		T inject( Member member, InternalContext context ) {
			ExternalContext<?> previous = context.getExternalContext();
			context.setExternalContext(externalContext);
			try {
				return factory.create(context);
			} finally {
				context.setExternalContext(previous);
			}
		}
	}

	private static Object[] getParameters( Member member, InternalContext context,
										   ParameterInjector[] parameterInjectors ) {
		if (parameterInjectors == null) {
			return null;
		}

		Object[] parameters = new Object[parameterInjectors.length];
		for ( int i = 0; i < parameters.length; i++ ) {
			parameters[i] = parameterInjectors[i].inject(member, context);
		}
		return parameters;
	}

	void inject( Object o, InternalContext context ) {
		List<Injector> injectors = this.injectors.get(o.getClass());
		for ( Injector injector : injectors ) {
			injector.inject(context, o);
		}
	}

	<T> T inject( Class<T> implementation, InternalContext context ) {
		try {
			ConstructorInjector<T> constructor = getConstructor(implementation);
			return implementation.cast(
					constructor.construct(context, implementation));
		} catch ( Exception e ) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	<T> T getInstance( Class<T> type, String name, InternalContext context ) {
		ExternalContext<?> previous = context.getExternalContext();
		Key<T> key = Key.newInstance(type, name);
		context.setExternalContext(ExternalContext.newInstance(null, key, this));
		try {
			// 根据type和name构成的Key去获取对应的InternalFactory实现
			InternalFactory o = getFactory(key);
			if (o != null) {
				// 使用InternalFactory所规定的对象构建方法返回对象的实例
				return getFactory(key).create(context);
			} else {
				return null;
			}
		} finally {
			context.setExternalContext(previous);
		}
	}

	<T> T getInstance( Class<T> type, InternalContext context ) {
		return getInstance(type, DEFAULT_NAME, context);
	}

	public void inject( final Object o ) {
		callInContext(new ContextualCallable<Void>() {
			public Void call( InternalContext context ) {
				inject(o, context);
				return null;
			}
		});
	}

	public <T> T inject( final Class<T> implementation ) {
		return callInContext(new ContextualCallable<T>() {
			public T call( InternalContext context ) {
				return inject(implementation, context);
			}
		});
	}

	// 获取对象的实现
	public <T> T getInstance( final Class<T> type, final String name ) {
		return callInContext(new ContextualCallable<T>() {
			public T call( InternalContext context ) {
				return getInstance(type, name, context);
			}
		});
	}

	public <T> T getInstance( final Class<T> type ) {
		return callInContext(new ContextualCallable<T>() {
			public T call( InternalContext context ) {
				return getInstance(type, context);
			}
		});
	}

	public Set<String> getInstanceNames( final Class<?> type ) {
        Set<String> names = factoryNamesByType.get(type);
        if (names == null) {
            names = Collections.emptySet();
        }
        return names;
	}

	ThreadLocal<Object[]> localContext =
			new ThreadLocal<Object[]>() {
				@Override
				protected Object[] initialValue() {
					return new Object[1];
				}
			};

	/**
	 * Looks up thread local context. Creates (and removes) a new context if necessary.
	 * 查找线程安全的执行上下文环境，根据要求创建或者销毁执行环境
	 */
	<T> T callInContext( ContextualCallable<T> callable ) {
		// 从ThreadLocal变量中获取一个执行上下文环境
		Object[] reference = localContext.get();
		if (reference[0] == null) {
			// 如果执行上下文环境不存在，则创建一个新的环境
			reference[0] = new InternalContext(this);
			try {
				// 直接调用回调接口完成逻辑
				return callable.call((InternalContext) reference[0]);
			} finally {
				// 调用完成后恢复原状
				// Only remove the context if this call created it.
				reference[0] = null;
				// WW-3768: ThreadLocal was not removed
				localContext.remove();
			}
		} else {
			// Someone else will clean up this context.
			// 如果执行上下文环境已经存在，直接调用回调接口完成逻辑
			// 这里没有做恢复原状的操作，是因为调用者既然能操作localContext，清理并恢复原状的操作也有机会去完成
			return callable.call((InternalContext) reference[0]);
		}
	}

	interface ContextualCallable<T> {

		T call( InternalContext context );
	}

	/**
	 * Gets a constructor function for a given implementation class.
	 */
	@SuppressWarnings("unchecked")
	<T> ConstructorInjector<T> getConstructor( Class<T> implementation ) {
		return constructors.get(implementation);
	}

	final ThreadLocal<Object> localScopeStrategy =
			new ThreadLocal<Object>();

	public void setScopeStrategy( Scope.Strategy scopeStrategy ) {
		this.localScopeStrategy.set(scopeStrategy);
	}

	public void removeScopeStrategy() {
		this.localScopeStrategy.remove();
	}

	/**
	 * Injects a field or method in a given object.
	 */
	interface Injector extends Serializable {

		void inject( InternalContext context, Object o );
	}

	static class MissingDependencyException extends Exception {

		MissingDependencyException( String message ) {
			super(message);
		}
	}
}
