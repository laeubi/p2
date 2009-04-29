/*******************************************************************************
 * Copyright (c) 2008, 2009 Code 9 and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: 
 *   Code 9 - initial API and implementation
 *   EclipseSource - ongoing development
 ******************************************************************************/
package org.eclipse.equinox.p2.publisher.eclipse;

import java.io.File;
import java.util.*;
import org.eclipse.equinox.internal.p2.publisher.VersionedName;
import org.eclipse.equinox.internal.p2.publisher.eclipse.DataLoader;
import org.eclipse.equinox.internal.p2.publisher.eclipse.IProductDescriptor;
import org.eclipse.equinox.internal.provisional.frameworkadmin.BundleInfo;
import org.eclipse.equinox.internal.provisional.frameworkadmin.ConfigData;
import org.eclipse.equinox.internal.provisional.p2.core.Version;
import org.eclipse.equinox.p2.publisher.AbstractAdvice;
import org.eclipse.equinox.p2.publisher.AbstractPublisherAction;
import org.eclipse.equinox.p2.publisher.actions.ILicenseAdvice;

/**
 * Provide advice derived from the .product file.  The product can give some info on 
 * launching as well as the configuration (bundles, properties, ...)
 */
public class ProductFileAdvice extends AbstractAdvice implements ILicenseAdvice, IExecutableAdvice, IConfigAdvice, IBrandingAdvice {

	private final static String OSGI_SPLASH_PATH = "osgi.splashPath"; //$NON-NLS-1$
	private final static String SPLASH_PREFIX = "platform:/base/plugins/"; //$NON-NLS-1$
	private IProductDescriptor product;
	private String configSpec;
	private String os;
	private ConfigData configData = null;

	protected String getId() {
		return product.getId();
	}

	protected Version getVersion() {
		return Version.parseVersion(product.getVersion());
	}

	/**
	 * Constructs a new ProductFileAdvice for a given product file and a
	 * particular configuration. Configurations are 
	 * specified as: ws.os.arch where:
	 *  ws is the windowing system
	 *  os is the operating system
	 *  arch is the architecture
	 */
	public ProductFileAdvice(IProductDescriptor product, String configSpec) {
		this.product = product;
		this.configSpec = configSpec;
		os = AbstractPublisherAction.parseConfigSpec(configSpec)[1];
		configData = getConfigData();
	}

	/**
	 * Returns the program arguments for this product.  
	 */
	public String[] getProgramArguments() {
		String line = product.getProgramArguments(os);
		return AbstractPublisherAction.getArrayFromString(line, " "); //$NON-NLS-1$
	}

	/**
	 * Returns the VM arguments for this product.
	 */
	public String[] getVMArguments() {
		String line = product.getVMArguments(os);
		return AbstractPublisherAction.getArrayFromString(line, " "); //$NON-NLS-1$
	}

	/**
	 * Returns the Bundles that constitute this product.  These
	 * bundles may be specified in the .product file, .product file configuration
	 * area, config.ini file, or a combination of these three places.
	 */
	public BundleInfo[] getBundles() {
		return configData.getBundles();
	}

	/**
	 * Returns the properties associated with this product.  These
	 * properties may be defined in the .product file, the config.ini
	 * file, or both.
	 */
	public Properties getProperties() {
		Properties result = new Properties();
		result.putAll(configData.getProperties());
		result.putAll(product.getConfigurationProperties());
		return result;
	}

	/**
	 * Returns the name of the launcher.  This should be the OS-independent
	 * name. That is, ".exe" etc. should not be included.
	 * 
	 * @return the name of the branded launcher or <code>null</code> if none.
	 */
	public String getExecutableName() {
		return product.getLauncherName();
	}

	/**
	 * Returns the product file parser that this advice is working on
	 */
	public IProductDescriptor getProductFile() {
		return product;
	}

	/**
	 * Returns the icons defined for this product
	 */
	public String[] getIcons() {
		return product.getIcons(os);
	}

	/**
	 * Returns the operating system that this advice is configured to work with.
	 */
	public String getOS() {
		return this.os;
	}

	/**
	 * Returns the license text for this product
	 */
	public String getLicenseURL() {
		return product.getLicenseURL();
	}

	/**
	 * Returns the license URL for this product
	 */
	public String getLicenseText() {
		return product.getLicenseText();
	}

	private ConfigData getConfigData() {
		DataLoader loader = createDataLoader();
		ConfigData result;
		if (loader != null)
			result = loader.getConfigData();
		else
			result = generateConfigData();

		addProductFileBundles(result); // these are the bundles specified in the <plugins/> tag
		addProductFileConfigBundles(result); // these are the bundles specified in the <configurations> tag in the product file

		if (product.getProductId() != null)
			result.setProperty("eclipse.product", product.getProductId()); //$NON-NLS-1$
		if (product.getApplication() != null)
			result.setProperty("eclipse.application", product.getApplication()); //$NON-NLS-1$
		String location = getSplashLocation();
		if (location != null)
			result.setProperty(OSGI_SPLASH_PATH, SPLASH_PREFIX + location);
		return result;
	}

	private void addProductFileConfigBundles(ConfigData configData) {
		Set versionBoundBundles = new HashSet();
		Map unboundedBundles = new HashMap();

		BundleInfo[] bundles = configData.getBundles();
		for (int i = 0; i < bundles.length; i++) {
			// For each bundle we know about, cache it.  If the bundle doesn't have a version
			// add it to a list of bundles by name
			BundleInfo bundleInfo = bundles[i];
			if (bundleInfo.getVersion() == null || bundleInfo.getVersion().equals("0.0.0")) { //$NON-NLS-1$
				bundleInfo.setVersion("0.0.0"); //$NON-NLS-1$
				addUnboundedBundle(unboundedBundles, bundleInfo);
			} else {
				versionBoundBundles.add(bundleInfo);
				addUnboundedBundle(unboundedBundles, bundleInfo);
			}
		}

		List bundleInfos = product.getBundleInfos();
		for (Iterator i = bundleInfos.iterator(); i.hasNext();) {
			// For each bundle that has configuration information, if the bundle is in the 
			// bundles bound by version, add the "configured" bundle instead
			// If the bundle is not bound to a version, then replace all bounded versions
			// with this one.  Otherwise, just add this one (don't replace)
			BundleInfo bundleInfo = (BundleInfo) i.next();

			if (versionBoundBundles.contains(bundleInfo)) {
				// If we found a version with the same name and version, replace it with the "configured" bundle
				configData.removeBundle(bundleInfo);
				configData.addBundle(bundleInfo);
			} else if (bundleInfo.getVersion() == null || bundleInfo.getVersion().equals("0.0.0")) {//$NON-NLS-1$
				// If we don't have a version number, look for all bundles that match by name
				List list = (List) unboundedBundles.get(bundleInfo.getSymbolicName());
				if (list == null)
					configData.addBundle(bundleInfo);
				else
					for (Iterator iterator = list.iterator(); iterator.hasNext();) {
						BundleInfo target = (BundleInfo) iterator.next();
						target.setStartLevel(bundleInfo.getStartLevel());
						target.setMarkedAsStarted(bundleInfo.isMarkedAsStarted());
					}
			} else {
				// Otherwise we have a version, but we could not match it, so just add this one.
				configData.addBundle(bundleInfo);
			}

		}
	}

	private void addUnboundedBundle(Map data, BundleInfo bundleInfo) {
		if (!data.containsKey(bundleInfo.getSymbolicName()))
			data.put(bundleInfo.getSymbolicName(), new LinkedList());
		((LinkedList) data.get(bundleInfo.getSymbolicName())).add(bundleInfo);
	}

	private void addProductFileBundles(ConfigData configData) {
		List bundles = product.getBundles(true);
		Set set = new HashSet();
		set.addAll(Arrays.asList(configData.getBundles()));

		for (Iterator i = bundles.iterator(); i.hasNext();) {
			VersionedName name = (VersionedName) i.next();
			BundleInfo bundleInfo = new BundleInfo();
			bundleInfo.setSymbolicName(name.getId());
			bundleInfo.setVersion(name.getVersion().toString());
			if (!set.contains(bundleInfo))
				configData.addBundle(bundleInfo);
		}
	}

	private ConfigData generateConfigData() {
		ConfigData result = new ConfigData(null, null, null, null);
		if (product.useFeatures())
			return result;

		// Add all the bundles here.  We replace / update them later
		// if we find configuration information
		List bundles = product.getBundles(true);
		for (Iterator i = bundles.iterator(); i.hasNext();) {
			VersionedName name = (VersionedName) i.next();
			BundleInfo bundleInfo = new BundleInfo();
			bundleInfo.setSymbolicName(name.getId());
			bundleInfo.setVersion(name.getVersion().toString());
			result.addBundle(bundleInfo);
		}
		return result;
	}

	private String getSplashLocation() {
		return product.getSplashLocation();
	}

	protected String getConfigSpec() {
		return configSpec;
	}

	protected boolean matchConfig(String spec, boolean includeDefault) {
		if (spec != null) {
			String targetOS = AbstractPublisherAction.parseConfigSpec(spec)[1];
			return os.equals(targetOS);
		}
		return true;
	}

	private DataLoader createDataLoader() {
		String location = product.getConfigIniPath(os);
		if (location == null)
			location = product.getConfigIniPath(null);
		if (location == null)
			return null;
		File configFile = new File(location);

		// We are assuming we are always relative from the product file
		// However PDE tooling puts us relative from the workspace
		if (!configFile.isAbsolute())
			configFile = new File(product.getLocation().getParentFile(), location);
		// TODO need to figure out what to do for the launcher location here...
		// for now just give any old path that has a parent
		return new DataLoader(configFile, new File(product.getLauncherName()).getAbsoluteFile());
	}

}
