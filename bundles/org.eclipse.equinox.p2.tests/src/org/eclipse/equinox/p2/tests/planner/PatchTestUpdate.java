/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.equinox.p2.tests.planner;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.equinox.internal.provisional.p2.director.*;
import org.eclipse.equinox.internal.provisional.p2.engine.IEngine;
import org.eclipse.equinox.internal.provisional.p2.engine.IProfile;
import org.eclipse.equinox.internal.provisional.p2.metadata.*;
import org.eclipse.equinox.p2.tests.AbstractProvisioningTest;
import org.eclipse.osgi.service.resolver.VersionRange;
import org.osgi.framework.Version;

public class PatchTestUpdate extends AbstractProvisioningTest {
	private static final String PP2 = "PatchForIUP2";
	private static final String PP1 = "PatchForIUP1";
	private static final String P2 = "P2";
	private static final String P1 = "P1";
	private static final String P2_FEATURE = "p2.feature";
	private IInstallableUnit p2Feature;
	private IInstallableUnit p2Feature20;
	private IInstallableUnit p1;
	private IInstallableUnit p2;
	private IInstallableUnitPatch pp1;
	private IInstallableUnitPatch pp2;
	private IInstallableUnit p2b;
	private IInstallableUnit p1b;
	private IProfile profile1;
	private IPlanner planner;
	private IEngine engine;

	protected void setUp() throws Exception {
		super.setUp();
		p2Feature = createIU(P2_FEATURE, new Version(1, 0, 0), new RequiredCapability[] {MetadataFactory.createRequiredCapability(IInstallableUnit.NAMESPACE_IU_ID, P1, new VersionRange("[1.0.0, 1.0.0]"), null, false, false, true), MetadataFactory.createRequiredCapability(IInstallableUnit.NAMESPACE_IU_ID, P2, new VersionRange("[1.0.0, 1.0.0]"), null, false, false, true)});
		p1 = createIU(P1, new Version(1, 0, 0), true);
		p2 = createIU(P2, new Version(1, 0, 0), true);
		p1b = createIU(P1, new Version(1, 1, 1), true);
		p2b = createIU(P2, new Version(1, 1, 1), true);

		RequirementChange changepp1 = new RequirementChange(MetadataFactory.createRequiredCapability(IInstallableUnit.NAMESPACE_IU_ID, P1, VersionRange.emptyRange, null, false, false, false), MetadataFactory.createRequiredCapability(IInstallableUnit.NAMESPACE_IU_ID, P1, new VersionRange("[1.1.1, 1.1.1]"), null, false, false, true));
		RequiredCapability lifeCyclepp1 = MetadataFactory.createRequiredCapability(IInstallableUnit.NAMESPACE_IU_ID, P2_FEATURE, new VersionRange("[1.0.0, 1.0.0]"), null, false, false, true);
		RequiredCapability[][] scopepp1 = new RequiredCapability[][] {{MetadataFactory.createRequiredCapability(IInstallableUnit.NAMESPACE_IU_ID, P2_FEATURE, new VersionRange("[1.0.0,1.0.0]"), null, false, false)}};
		pp1 = createIUPatch(PP1, new Version("3.0.0"), true, new RequirementChange[] {changepp1}, scopepp1, lifeCyclepp1);

		RequirementChange changepp2 = new RequirementChange(MetadataFactory.createRequiredCapability(IInstallableUnit.NAMESPACE_IU_ID, P2, VersionRange.emptyRange, null, false, false, false), MetadataFactory.createRequiredCapability(IInstallableUnit.NAMESPACE_IU_ID, P2, new VersionRange("[1.1.1, 1.1.1]"), null, false, false, true));
		RequiredCapability lifeCyclepp2 = MetadataFactory.createRequiredCapability(IInstallableUnit.NAMESPACE_IU_ID, P2_FEATURE, new VersionRange("[1.0.0, 1.0.0]"), null, false, false, true);
		RequiredCapability[][] scopepp2 = new RequiredCapability[][] {{MetadataFactory.createRequiredCapability(IInstallableUnit.NAMESPACE_IU_ID, P2_FEATURE, new VersionRange("[1.0.0, 1.0.0]"), null, false, false)}};
		pp2 = createIUPatch(PP2, new Version("5.0.0"), true, new RequirementChange[] {changepp2}, scopepp2, lifeCyclepp2);

		p2Feature20 = createIU(P2_FEATURE, new Version(2, 0, 0), new RequiredCapability[] {MetadataFactory.createRequiredCapability(IInstallableUnit.NAMESPACE_IU_ID, P1, new VersionRange("[1.0.0, 1.0.0]"), null, false, false, true), MetadataFactory.createRequiredCapability(IInstallableUnit.NAMESPACE_IU_ID, P2, new VersionRange("[1.0.0, 1.0.0]"), null, false, false, true)});
		createTestMetdataRepository(new IInstallableUnit[] {p2Feature, p1, p2, p1b, p2b, pp1, pp2, p2Feature20});

		profile1 = createProfile("TestProfile." + getName());
		planner = createPlanner();
		engine = createEngine();

		if (!install(profile1, new IInstallableUnit[] {p2Feature, pp1, pp2}, true, planner, engine).isOK())
			fail("Setup failed");

		assertProfileContainsAll("Profile setup incorrectly", profile1, new IInstallableUnit[] {p2Feature, p1b, p2b, pp1, pp2});
	}

	public void testUpdate() {
		//The update of the feature is expected to fail because the patches are installed without flexibility (strict mode) 
		ProfileChangeRequest req1 = new ProfileChangeRequest(profile1);
		req1.addInstallableUnits(new IInstallableUnit[] {p2Feature20});
		req1.setInstallableUnitInclusionRules(p2Feature20, PlannerHelper.createStrictInclusionRule(p2Feature20));
		req1.removeInstallableUnits(new IInstallableUnit[] {p2Feature});
		ProvisioningPlan plan = planner.getProvisioningPlan(req1, null, null);
		assertEquals(IStatus.ERROR, plan.getStatus().getSeverity());
	}
}
