/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Genuitec, LLC
 *******************************************************************************/
package org.eclipse.equinox.internal.provisional.p2.metadata;

import java.util.*;
import java.util.Map.Entry;
import org.eclipse.core.runtime.Assert;
import org.eclipse.equinox.internal.p2.metadata.*;
import org.eclipse.osgi.service.resolver.VersionRange;
import org.osgi.framework.Version;

/**
 * A factory class for instantiating various p2 metadata objects.
 */
public class MetadataFactory {
	public static class InstallableUnitDescription {
		protected InstallableUnit unit;

		public InstallableUnitDescription() {
			super();
		}

		public void addProvidedCapabilities(Collection additional) {
			if (additional == null || additional.size() == 0)
				return;
			ProvidedCapability[] current = unit().getProvidedCapabilities();
			ProvidedCapability[] result = new ProvidedCapability[additional.size() + current.length];
			System.arraycopy(current, 0, result, 0, current.length);
			int j = current.length;
			for (Iterator i = additional.iterator(); i.hasNext();)
				result[j++] = (ProvidedCapability) i.next();
			unit().setCapabilities(result);
		}

		public void addRequiredCapabilities(Collection additional) {
			if (additional == null || additional.size() == 0)
				return;
			RequiredCapability[] current = unit().getRequiredCapabilities();
			RequiredCapability[] result = new RequiredCapability[additional.size() + current.length];
			System.arraycopy(current, 0, result, 0, current.length);
			int j = current.length;
			for (Iterator i = additional.iterator(); i.hasNext();)
				result[j++] = (RequiredCapability) i.next();
			unit().setRequiredCapabilities(result);
		}

		public void addTouchpointData(TouchpointData data) {
			Assert.isNotNull(data);
			unit().addTouchpointData(data);
		}

		public String getId() {
			return unit().getId();
		}

		public ProvidedCapability[] getProvidedCapabilities() {
			return unit().getProvidedCapabilities();
		}

		public RequiredCapability[] getRequiredCapabilities() {
			return unit().getRequiredCapabilities();
		}

		/**
		 * Returns the current touchpoint data on this installable unit description. The
		 * touchpoint data may change if further data is added to the description.
		 * 
		 * @return The current touchpoint data on this description
		 */
		public TouchpointData[] getTouchpointData() {
			return unit().getTouchpointData();

		}

		public Version getVersion() {
			return unit().getVersion();
		}

		public void setArtifacts(IArtifactKey[] value) {
			unit().setArtifacts(value);
		}

		public void setCapabilities(ProvidedCapability[] exportedCapabilities) {
			unit().setCapabilities(exportedCapabilities);
		}

		public void setCopyright(Copyright copyright) {
			unit().setCopyright(copyright);
		}

		public void setFilter(String filter) {
			unit().setFilter(filter);
		}

		public void setId(String id) {
			unit().setId(id);
		}

		public void setLicense(License license) {
			unit().setLicense(license);
		}

		public void setProperty(String key, String value) {
			unit().setProperty(key, value);
		}

		public void setRequiredCapabilities(RequiredCapability[] capabilities) {
			unit().setRequiredCapabilities(capabilities);
		}

		public void setSingleton(boolean singleton) {
			unit().setSingleton(singleton);
		}

		public void setTouchpointType(TouchpointType type) {
			unit().setTouchpointType(type);
		}

		public void setUpdateDescriptor(IUpdateDescriptor updateInfo) {
			unit().setUpdateDescriptor(updateInfo);
		}

		public void setVersion(Version newVersion) {
			unit().setVersion(newVersion);
		}

		InstallableUnit unit() {
			if (unit == null) {
				unit = new InstallableUnit();
				unit.setArtifacts(new IArtifactKey[0]);
			}
			return unit;
		}

		IInstallableUnit unitCreate() {
			IInstallableUnit result = unit();
			this.unit = null;
			return result;
		}
	}

	public static class InstallableUnitFragmentDescription extends InstallableUnitDescription {
		public void setHost(RequiredCapability[] hostRequirements) {
			((InstallableUnitFragment) unit()).setHost(hostRequirements);
		}

		InstallableUnit unit() {
			if (unit == null)
				unit = new InstallableUnitFragment();
			return unit;
		}
	}

	public static class InstallableUnitPatchDescription extends InstallableUnitDescription {

		public void setApplicabilityScope(RequiredCapability[][] applyTo) {
			if (applyTo == null)
				throw new IllegalArgumentException("A patch scope can not be null"); //$NON-NLS-1$
			((InstallableUnitPatch) unit()).setApplicabilityScope(applyTo);
		}

		public void setLifeCycle(RequiredCapability lifeCycle) {
			((InstallableUnitPatch) unit()).setLifeCycle(lifeCycle);
		}

		public void setRequirementChanges(RequirementChange[] changes) {
			((InstallableUnitPatch) unit()).setRequirementsChange(changes);
		}

		InstallableUnit unit() {
			if (unit == null) {
				unit = new InstallableUnitPatch();
				((InstallableUnitPatch) unit()).setApplicabilityScope(new RequiredCapability[0][0]);
			}
			return unit;
		}
	}

	/**
	 * Singleton touchpoint data for a touchpoint with no instructions.
	 */
	private static final TouchpointData EMPTY_TOUCHPOINT_DATA = new TouchpointData(Collections.EMPTY_MAP);

	private static TouchpointType[] typeCache = new TouchpointType[5];

	private static int typeCacheOffset;

	/**
	 * Returns an {@link IInstallableUnit} based on the given 
	 * description.  Once the installable unit has been created, the information is 
	 * discarded from the description object.
	 * 
	 * @param description The description of the unit to create
	 * @return The created installable unit
	 */
	public static IInstallableUnit createInstallableUnit(InstallableUnitDescription description) {
		Assert.isNotNull(description);
		return description.unitCreate();
	}

	/**
	 * Returns an {@link IInstallableUnitFragment} based on the given 
	 * description.  Once the fragment has been created, the information is 
	 * discarded from the description object.
	 * 
	 * @param description The description of the unit to create
	 * @return The created installable unit fragment
	 */
	public static IInstallableUnitFragment createInstallableUnitFragment(InstallableUnitFragmentDescription description) {
		Assert.isNotNull(description);
		return (IInstallableUnitFragment) description.unitCreate();
	}

	/**
	 * Returns an {@link IInstallableUnitPatch} based on the given 
	 * description.  Once the patch installable unit has been created, the information is 
	 * discarded from the description object.
	 * 
	 * @param description The description of the unit to create
	 * @return The created installable unit patch
	 */
	public static IInstallableUnitPatch createInstallableUnitPatch(InstallableUnitPatchDescription description) {
		Assert.isNotNull(description);
		return (IInstallableUnitPatch) description.unitCreate();
	}

	/**
	 * Returns a {@link ProvidedCapability} with the given values.
	 * 
	 * @param namespace The capability namespace
	 * @param name The capability name
	 * @param version The capability version
	 */
	public static ProvidedCapability createProvidedCapability(String namespace, String name, Version version) {
		return new ProvidedCapability(namespace, name, version);
	}

	/**
	 * Returns a {@link RequiredCapability} with the given values.
	 * 
	 * @param namespace The capability namespace
	 * @param name The required capability name
	 * @param range The range of versions that are required, or <code>null</code>
	 * to indicate that any version will do.
	 * @param filter The filter used to evaluate whether this capability is applicable in the
	 * current environment, or <code>null</code> to indicate this capability is always applicable
	 * @param optional <code>true</code> if this required capability is optional,
	 * and <code>false</code> otherwise.
	 * @param multiple <code>true</code> if this capability can be satisfied by multiple provided capabilities, or it requires exactly one match
	 */
	public static RequiredCapability createRequiredCapability(String namespace, String name, VersionRange range, String filter, boolean optional, boolean multiple) {
		return new RequiredCapability(namespace, name, range, filter, optional, multiple);
	}

	public static RequiredCapability createRequiredCapability(String namespace, String name, VersionRange range, String filter, boolean optional, boolean multiple, boolean greedy) {
		return new RequiredCapability(namespace, name, range, filter, optional, multiple, greedy);
	}

	/**
	 * Returns an {@link IInstallableUnit} that represents the given
	 * unit bound to the given fragments.
	 * 
	 * @see IInstallableUnit#isResolved()
	 * @param unit The unit to be bound
	 * @param fragments The fragments to be bound
	 * @return A resolved installable unit
	 */
	public static IInstallableUnit createResolvedInstallableUnit(IInstallableUnit unit, IInstallableUnitFragment[] fragments) {
		if (unit.isResolved())
			return unit;
		Assert.isNotNull(unit);
		Assert.isNotNull(fragments);
		return new ResolvedInstallableUnit(unit, fragments);

	}

	/**
	 * Returns an instance of {@link TouchpointData} with the given instructions.
	 * 
	 * @param instructions The instructions for the touchpoint data.
	 * @return The created touchpoint data
	 */
	public static TouchpointData createTouchpointData(Map instructions) {
		Assert.isNotNull(instructions);
		//copy the map to protect against subsequent change by caller
		if (instructions.isEmpty())
			return EMPTY_TOUCHPOINT_DATA;

		Map result = new LinkedHashMap(instructions.size());
		for (Iterator iterator = instructions.entrySet().iterator(); iterator.hasNext();) {
			Entry entry = (Entry) iterator.next();
			Object value = entry.getValue();
			if (value == null || value instanceof String)
				value = createTouchpointInstruction((String) value, null);

			result.put(entry.getKey(), value);
		}
		return new TouchpointData(result);
	}

	public static TouchpointInstruction createTouchpointInstruction(String body, String importAttribute) {
		return new TouchpointInstruction(body, importAttribute);
	}

	/**
	 * Returns a {@link TouchpointType} with the given id and version.
	 * 
	 * @param id The touchpoint id
	 * @param version The touchpoint version
	 * @return A touchpoint type instance with the given id and version
	 */
	public static TouchpointType createTouchpointType(String id, Version version) {
		Assert.isNotNull(id);
		Assert.isNotNull(version);

		if (id.equals(TouchpointType.NONE.getId()) && version.equals(TouchpointType.NONE.getVersion()))
			return TouchpointType.NONE;

		synchronized (typeCache) {
			TouchpointType result = getCachedTouchpointType(id, version);
			if (result != null)
				return result;
			result = new TouchpointType(id, version);
			putCachedTouchpointType(result);
			return result;
		}
	}

	public static IUpdateDescriptor createUpdateDescriptor(String id, VersionRange range, int severity, String description) {
		return new UpdateDescriptor(id, range, severity, description);
	}

	private static TouchpointType getCachedTouchpointType(String id, Version version) {
		for (int i = 0; i < typeCache.length; i++) {
			if (typeCache[i] != null && typeCache[i].getId().equals(id) && typeCache[i].getVersion().equals(version))
				return typeCache[i];
		}
		return null;
	}

	private static void putCachedTouchpointType(TouchpointType result) {
		//simple rotating buffer
		typeCache[typeCacheOffset] = result;
		typeCacheOffset = (typeCacheOffset + 1) % typeCache.length;
	}
}
