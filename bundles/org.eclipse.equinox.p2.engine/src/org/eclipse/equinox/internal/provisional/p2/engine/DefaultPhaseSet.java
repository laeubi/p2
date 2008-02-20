/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.provisional.p2.engine;

import org.eclipse.equinox.internal.provisional.p2.engine.phases.*;

public class DefaultPhaseSet extends PhaseSet {

	public DefaultPhaseSet() {
		super(new Phase[] {new Collect(100), new Unconfigure(10), new Uninstall(50), new Property(1), new Install(50), new Configure(10)});
	}

}
