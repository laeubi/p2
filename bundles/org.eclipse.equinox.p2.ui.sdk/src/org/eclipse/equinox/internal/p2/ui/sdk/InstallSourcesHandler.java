/*******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Contributors - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.ui.sdk;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.equinox.internal.p2.director.ProfileChangeRequest;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.engine.*;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.operations.*;
import org.eclipse.equinox.p2.ui.ProvisioningUI;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.statushandlers.StatusManager;

/**
 * InstallSourcesHandler toggles the installation of source bundles.
 * When enabled, it sets the org.eclipse.update.install.sources profile property
 * to "true" and triggers an update operation to fetch missing sources.
 * When disabled, it sets the property to "false".
 *
 * @since 3.5
 */
public class InstallSourcesHandler extends AbstractHandler {

	private static final String INSTALL_SOURCES_PROPERTY = "org.eclipse.update.install.sources"; //$NON-NLS-1$

	@Override
	public Object execute(ExecutionEvent event) {
		// Get the provisioning UI and check for a valid profile
		ProvisioningUI provUI = getProvisioningUI();
		String profileId = provUI.getProfileId();
		IProvisioningAgent agent = provUI.getSession().getProvisioningAgent();
		IProfile profile = null;
		
		if (agent != null) {
			IProfileRegistry registry = agent.getService(IProfileRegistry.class);
			if (registry != null) {
				profile = registry.getProfile(profileId);
			}
		}
		
		if (profile == null) {
			MessageDialog.openInformation(null, ProvSDKMessages.Handler_SDKUpdateUIMessageTitle,
					ProvSDKMessages.Handler_CannotLaunchUI);
			StatusManager.getManager().handle(ProvSDKUIActivator.getNoSelfProfileStatus());
			return null;
		}

		// Toggle the install sources property
		String currentValue = profile.getProperty(INSTALL_SOURCES_PROPERTY);
		boolean installSources = !"true".equals(currentValue); //$NON-NLS-1$
		
		// Create a profile change request to set the property
		ProfileChangeRequest request = new ProfileChangeRequest(profile);
		request.setProfileProperty(INSTALL_SOURCES_PROPERTY, String.valueOf(installSources));
		
		// Create and execute a provisioning operation
		ProvisioningJob job = new ProvisioningJob(ProvSDKMessages.InstallSourcesHandler_JobName, 
				provUI.getSession()) {
			
			@Override
			public IStatus runModal(IProgressMonitor monitor) {
				SubMonitor sub = SubMonitor.convert(monitor, ProvSDKMessages.InstallSourcesHandler_ProgressTaskName, 100);
				
				try {
					// Apply the profile property change
					IPlanner planner = provUI.getSession().getPlanner();
					IEngine engine = provUI.getSession().getEngine();
					
					ProvisioningContext context = new ProvisioningContext(agent);
					IProvisioningPlan plan = planner.getProvisioningPlan(request, context, sub.newChild(30));
					
					if (plan.getStatus().isOK()) {
						IStatus result = engine.perform(plan, sub.newChild(70));
						if (result.isOK()) {
							// If we enabled install sources, trigger an update to fetch missing sources
							if (installSources) {
								PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
									MessageDialog.openInformation(PlatformUI.getWorkbench().getModalDialogShellProvider().getShell(),
											ProvSDKMessages.InstallSourcesHandler_EnabledTitle,
											ProvSDKMessages.InstallSourcesHandler_EnabledMessage);
									// Trigger an update operation to fetch sources
									triggerUpdateForSources(provUI);
								});
							} else {
								PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
									MessageDialog.openInformation(PlatformUI.getWorkbench().getModalDialogShellProvider().getShell(),
											ProvSDKMessages.InstallSourcesHandler_DisabledTitle,
											ProvSDKMessages.InstallSourcesHandler_DisabledMessage);
								});
							}
						}
						return result;
					} else {
						return plan.getStatus();
					}
				} finally {
					sub.done();
				}
			}
		};
		
		job.setUser(true);
		job.schedule();
		
		return null;
	}

	/**
	 * Triggers an update operation to fetch missing source bundles
	 */
	private void triggerUpdateForSources(ProvisioningUI provUI) {
		UpdateOperation op = provUI.getUpdateOperation(null, null);
		
		Job updateJob = new Job(ProvSDKMessages.InstallSourcesHandler_UpdateJobName) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				IStatus result = op.resolveModal(monitor);
				if (result.isOK() && op.hasResolved()) {
					// Schedule the provisioning job
					ProvisioningJob provJob = op.getProvisioningJob(monitor);
					if (provJob != null) {
						provJob.schedule();
					}
				}
				return Status.OK_STATUS;
			}
		};
		updateJob.setUser(true);
		updateJob.schedule();
	}

	protected ProvisioningUI getProvisioningUI() {
		return ProvisioningUI.getDefaultUI();
	}
}
