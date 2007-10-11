/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.p2.ui.model;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.equinox.p2.artifact.repository.IArtifactRepository;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.ui.ProvisioningUtil;

/**
 * Element class that represents the root of an artifact
 * repository viewer.  Its children are all of the known
 * artifact repositories.
 * 
 * @since 3.4
 *
 */
public class AllArtifactRepositories extends ProvElement {

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.equinox.p2.ui.model.ProvElement#fetchChildren(java.lang.Object, org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected Object[] fetchChildren(Object o, IProgressMonitor monitor) {
		try {
			return ProvisioningUtil.getArtifactRepositories(monitor);
		} catch (ProvisionException e) {
			handleException(e, null);
		}
		return new IArtifactRepository[0];
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.model.IWorkbenchAdapter#getLabel(java.lang.Object)
	 */
	public String getLabel(Object o) {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.equinox.p2.ui.model.ProvElement#getParent(java.lang.Object)
	 */
	public Object getParent(Object o) {
		return null;
	}

}
