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
package org.springframework.ide.eclipse.beans.ui.refactoring.jdt;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RefactoringArguments;
import org.eclipse.ltk.core.refactoring.participants.RenameArguments;
import org.eclipse.ltk.core.refactoring.participants.RenameParticipant;
import org.springframework.ide.eclipse.beans.core.BeansCorePlugin;
import org.springframework.ide.eclipse.beans.core.model.IBeansConfig;
import org.springframework.ide.eclipse.beans.core.model.IBeansImport;
import org.springframework.ide.eclipse.beans.core.model.IBeansProject;

/**
 * Abstract super class for implementing {@link RenameParticipant}
 * @author Christian Dupuis
 */
public abstract class AbstractRenameRefactoringParticipant extends
		RenameParticipant {

	protected IProject project;

	protected Map<Object, Object> elements;

	@Override
	public RefactoringStatus checkConditions(IProgressMonitor pm,
			CheckConditionsContext context) throws OperationCanceledException {
		return new RefactoringStatus();
	}

	public void addElement(Object element, RefactoringArguments arguments) {
		elements.put(element, ((RenameArguments) arguments).getNewName());
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException,
			OperationCanceledException {
		if (!getArguments().getUpdateReferences()) {
			return null;
		}
		CompositeChange result = new CompositeChange(getName());
		Set<IBeansProject> projects = BeansCorePlugin.getModel().getProjects();
		for (IBeansProject beansProject : projects) {
			Set<IBeansConfig> beansConfigs = beansProject.getConfigs();
			for (IBeansConfig beansConfig : beansConfigs) {
				addChange(result, beansConfig.getElementResource(), pm);
				for (IBeansImport import_ : beansConfig.getImports()) {
					for (IBeansConfig config : import_.getImportedBeansConfigs()) {
						addChange(result, config.getElementResource(), pm);
					}
				}
			}
		}
		return (result.getChildren().length == 0) ? null : result;
	}

	protected abstract void addChange(CompositeChange result,
			IResource resource, IProgressMonitor pm) throws CoreException;

	protected IJavaElement[] getAffectedElements() {
		Set<Object> objects = elements.keySet();
		return objects
				.toArray(new IJavaElement[objects.size()]);
	}

	protected String[] getNewNames() {
		String[] result = new String[elements.size()];
		Iterator<Object> iter = elements.values().iterator();
		for (int i = 0; i < elements.size(); i++) {
			result[i] = iter.next().toString();
		}
		return result;
	}

	@Override
	public String getName() {
		return "Rename classes referenced in Spring Bean definitions";
	}
}
