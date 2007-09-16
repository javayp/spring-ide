/*******************************************************************************
 * Copyright (c) 2005, 2007 Spring IDE Developers
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Spring IDE Developers - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.beans.core.internal.model.validation.rules;

import java.util.Set;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.ide.eclipse.beans.core.internal.model.BeansModelUtils;
import org.springframework.ide.eclipse.beans.core.internal.model.validation.BeansValidationContext;
import org.springframework.ide.eclipse.beans.core.model.IBean;
import org.springframework.ide.eclipse.beans.core.model.IBeansConfig;
import org.springframework.ide.eclipse.beans.core.model.IBeansConfigSet;
import org.springframework.ide.eclipse.core.java.Introspector;
import org.springframework.ide.eclipse.core.java.JdtUtils;
import org.springframework.ide.eclipse.core.model.IModelElement;

/**
 * Helpers for validation rules.
 * 
 * @author Torsten Juergeleit
 * @author Christian Dupuis
 * @since 2.0
 */
public final class ValidationRuleUtils {

	public static final String PLACEHOLDER_PREFIX = "${";

	public static final String PLACEHOLDER_SUFFIX = "}";

	public static final String FACTORY_BEAN_REFERENCE_PREFIX = "&";

	public static final String FACTORY_BEAN_REFERENCE_REGEXP = "["
			+ FACTORY_BEAN_REFERENCE_PREFIX + "]";

	public static final String ASPECT_OF_METHOD_NAME = "aspectOf";

	/**
	 * Returns <code>true</code> if given text contains a placeholder, e.g.
	 * <code>${beansRef}</code>.
	 */
	public static boolean hasPlaceHolder(String text) {
		int pos = text.indexOf(PLACEHOLDER_PREFIX);
		return (pos != -1 && text.indexOf(PLACEHOLDER_SUFFIX, pos) != -1);
	}

	/**
	 * Returns <code>true</code> if the specified text is a reference to a
	 * factory bean, e.g. <code>&factoryBean</code>.
	 */
	public static boolean isFactoryBeanReference(String property) {
		return property.startsWith(FACTORY_BEAN_REFERENCE_PREFIX);
	}

	/**
	 * Returns the given {@link IBean bean}'s bean class name. For child beans
	 * the corresponding parents are resolved within the bean's
	 * {@link IBeansConfig} only.
	 */
	public static String getBeanClassName(IBean bean) {
		return getBeanClassName(bean, BeansModelUtils.getConfig(bean));
	}

	/**
	 * Returns the given {@link IBean bean}'s bean class name. For child beans
	 * the corresponding parents are resolved within the given context ({@link IBeansConfig}
	 * or {@link IBeansConfigSet}).
	 */
	public static String getBeanClassName(IBean bean, IModelElement context) {
		BeanDefinition bd = BeansModelUtils.getMergedBeanDefinition(bean,
				context);
		if (bd != null) {
			return bd.getBeanClassName();
		}
		return null;
	}

	/**
	 * Checks if a been registered in the {@link BeanDefinitionRegistry}.
	 */
	public static BeanDefinition getBeanDefinition(String beanName,
			String beanClass, BeansValidationContext context) {
		try {
			return context.getCompleteRegistry().getBeanDefinition(beanName);
		}
		catch (NoSuchBeanDefinitionException e) {
			// fall back for manual installation of the post processor
			for (String name : context.getCompleteRegistry()
					.getBeanDefinitionNames()) {
				try {
					BeanDefinition db = context.getCompleteRegistry()
							.getBeanDefinition(name);
					if (db.getBeanClassName() != null
							&& Introspector.doesExtend(JdtUtils.getJavaType(
									context.getRootElementProject(), db
											.getBeanClassName()), beanClass)) {
						return db;
					}
				}
				catch (BeanDefinitionStoreException e1) {
					// ignore here
				}
			}
			return null;
		}
	}

	/**
	 * Extracts the {@link IType} of a bean definition.
	 * <p>
	 * Honors <code>factory-method</code>s.
	 */
	public static IType extractBeanClass(BeanDefinition bd, IBean bean,
			String mergedClassName, BeansValidationContext context) {
		IType type = JdtUtils.getJavaType(BeansModelUtils.getProject(bean)
				.getProject(), mergedClassName);
		// 1. factory-method on bean
		if (bd.getFactoryMethodName() != null
				&& bd.getFactoryBeanName() == null) {
			type = extractTypeFromFactoryMethod(bd, type);
		}
		// 2. factory-method on factory-bean
		else if (bd.getFactoryBeanName() != null
				&& bd.getFactoryMethodName() != null) {
			try {
				AbstractBeanDefinition factoryBd = (AbstractBeanDefinition) context
						.getCompleteRegistry().getBeanDefinition(
								bd.getFactoryBeanName());
				IType factoryBeanType = extractBeanClass(factoryBd, bean,
						factoryBd.getBeanClassName(), context);
				if (factoryBeanType != null) {
					type = extractTypeFromFactoryMethod(bd, factoryBeanType);
				}
			}
			catch (NoSuchBeanDefinitionException e) {

			}
		}
		return type;
	}

	private static IType extractTypeFromFactoryMethod(
			BeanDefinition bd, IType type) {
		String factoryMethod = bd.getFactoryMethodName();
		try {
			int argCount = (!bd.isAbstract() ? bd
					.getConstructorArgumentValues().getArgumentCount() : -1);
			Set<IMethod> methods = Introspector.getAllMethods(type);
			for (IMethod method : methods) {
				if (factoryMethod.equals(method.getElementName())
						&& method.getParameterNames().length == argCount) {
					type = JdtUtils.getJavaTypeFromSignatureClassName(
							method.getReturnType(), type);
					break;
				}
			}
		}
		catch (JavaModelException e) {
		}
		return type;
	}
}
