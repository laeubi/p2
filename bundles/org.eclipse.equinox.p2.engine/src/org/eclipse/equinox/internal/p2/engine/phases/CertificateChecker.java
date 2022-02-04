/*******************************************************************************
 * Copyright (c) 2008, 2017 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.engine.phases;

import java.io.*;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.equinox.internal.p2.artifact.processors.pgp.PGPPublicKeyStore;
import org.eclipse.equinox.internal.p2.artifact.processors.pgp.PGPSignatureVerifier;
import org.eclipse.equinox.internal.p2.engine.*;
import org.eclipse.equinox.p2.core.*;
import org.eclipse.equinox.p2.core.UIServices.TrustInfo;
import org.eclipse.equinox.p2.engine.IProfile;
import org.eclipse.equinox.p2.engine.ProfileScope;
import org.eclipse.equinox.p2.repository.artifact.IArtifactDescriptor;
import org.eclipse.equinox.p2.repository.spi.PGPPublicKeyService;
import org.eclipse.osgi.service.security.TrustEngine;
import org.eclipse.osgi.signedcontent.*;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.*;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Checks the certificates or PGP signatures on a set of files or artifacts and
 * reports back any problems with unsigned artifacts, untrusted certificates, or
 * tampered content.
 */
public class CertificateChecker {
	private static final String DEBUG_PREFIX = "certificate checker"; //$NON-NLS-1$

	public static final String TRUSTED_KEY_STORE_PROPERTY = "pgp.trustedPublicKeys"; //$NON-NLS-1$

	/***
	 * Store the optional profile for PGP key handling
	 */
	private IProfile profile;

	/**
	 * Stores artifacts to check
	 */
	private Map<IArtifactDescriptor, File> artifacts = new HashMap<>();
	private final IProvisioningAgent agent;
	private final PGPPublicKeyService keyService;

	// Lazily loading
	private Supplier<PGPPublicKeyStore> trustedKeys = new Supplier<>() {
		private PGPPublicKeyStore cache = null;

		public PGPPublicKeyStore get() {
			if (cache == null) {
				cache = getPreferenceTrustedKeys();
				getContributedTrustedKeys().keySet().forEach(cache::addKey);
			}
			return cache;
		}
	};

	public CertificateChecker() {
		this(null);
	}

	public CertificateChecker(IProvisioningAgent agent) {
		this.agent = agent;
		artifacts = new HashMap<>();
		keyService = agent.getService(PGPPublicKeyService.class);

	}

	public IStatus start() {
		final BundleContext context = EngineActivator.getContext();
		ServiceReference<SignedContentFactory> contentFactoryRef = context.getServiceReference(SignedContentFactory.class);
		SignedContentFactory verifierFactory = context.getService(contentFactoryRef);
		try {
			return checkCertificates(verifierFactory);
		} finally {
			context.ungetService(contentFactoryRef);
		}
	}

	private IStatus checkCertificates(SignedContentFactory verifierFactory) {
		UIServices serviceUI = agent.getService(UIServices.class);
		ArrayList<Certificate> untrustedCertificates = new ArrayList<>();
		Map<IArtifactDescriptor, Collection<PGPPublicKey>> untrustedPGPArtifacts = new HashMap<>();
		Map<IArtifactDescriptor, File> unsigned = new HashMap<>();
		ArrayList<Certificate[]> untrustedChain = new ArrayList<>();
		Map<Certificate, Collection<File>> untrustedArtifacts = new HashMap<>();
		IStatus status = Status.OK_STATUS;
		if (artifacts.isEmpty() || serviceUI == null) {
			return status;
		}
		Set<PGPPublicKey> trustedKeySet = new HashSet<>();
		boolean isTrustedKeySetInitialized = false;
		for (Entry<IArtifactDescriptor, File> artifact : artifacts.entrySet()) {
			File artifactFile = artifact.getValue();
			try {
				SignedContent content = verifierFactory.getSignedContent(artifactFile);
				if (content.isSigned()) {
					SignerInfo[] signerInfo = content.getSignerInfos();
					if (Arrays.stream(signerInfo).noneMatch(SignerInfo::isTrusted)) {
						// Only record the untrusted elements if there are no trusted elements.
						for (SignerInfo element : signerInfo) {
							if (!element.isTrusted()) {
								Certificate[] certificateChain = element.getCertificateChain();
								if (!untrustedCertificates.contains(certificateChain[0])) {
									untrustedCertificates.add(certificateChain[0]);
									untrustedChain.add(certificateChain);
								}
								if (DebugHelper.DEBUG_CERTIFICATE_CHECKER_UNTRUSTED) {
									untrustedArtifacts.computeIfAbsent(certificateChain[0], key -> new ArrayList<>())
											.add(artifactFile);
								}
							}
						}
					}
				} else {
					// The keys are in this destination artifact's properties if and only if the
					// PGPSignatureVerifier verified the signatures against these keys.
					List<PGPPublicKey> verifiedKeys = PGPPublicKeyStore
							.readPublicKeys(
									artifact.getKey().getProperty(PGPSignatureVerifier.PGP_SIGNER_KEYS_PROPERTY_NAME))
							.stream().map(keyService::addKey).collect(Collectors.toList());
					if (!verifiedKeys.isEmpty()) {
						if (!isTrustedKeySetInitialized) {
							isTrustedKeySetInitialized = true;
							trustedKeySet.addAll(trustedKeys.get().all());
						}
						if (verifiedKeys.stream().noneMatch(trustedKeySet::contains)) {
							untrustedPGPArtifacts.put(artifact.getKey(), verifiedKeys);
						}
					} else {
						unsigned.put(artifact.getKey(), artifactFile);
					}
				}
			} catch (GeneralSecurityException e) {
				return new Status(IStatus.ERROR, EngineActivator.ID, Messages.CertificateChecker_SignedContentError, e);
			} catch (IOException e) {
				return new Status(IStatus.ERROR, EngineActivator.ID, Messages.CertificateChecker_SignedContentIOError, e);
			}
		}

		// log the unsigned artifacts if requested
		if (DebugHelper.DEBUG_CERTIFICATE_CHECKER_UNSIGNED && !unsigned.isEmpty()) {
			StringBuilder message = new StringBuilder("The following artifacts are unsigned:\n"); //$NON-NLS-1$
			for (File file : unsigned.values()) {
				message.append(NLS.bind("  {0}\n", file.getPath())); //$NON-NLS-1$
			}
			DebugHelper.debug(DEBUG_PREFIX, message.toString());
		}

		// log the untrusted certificates if requested
		if (DebugHelper.DEBUG_CERTIFICATE_CHECKER_UNTRUSTED && !untrustedCertificates.isEmpty()) {
			StringBuilder message = new StringBuilder("The following certificates are untrusted:\n"); //$NON-NLS-1$
			for (Certificate cert : untrustedArtifacts.keySet()) {
				message.append(cert.toString() + "\n"); //$NON-NLS-1$
				message.append("  used by the following artifacts:\n"); //$NON-NLS-1$
				for (File file : untrustedArtifacts.get(cert)) {
					message.append(NLS.bind("    {0}\n", file.getPath())); //$NON-NLS-1$
				}
			}
			DebugHelper.debug(DEBUG_PREFIX, message.toString());
		}
		Set<PGPPublicKey> untrustedPGPKeys = untrustedPGPArtifacts.values().stream().flatMap(Collection::stream)
				.collect(Collectors.toSet());
		if (DebugHelper.DEBUG_CERTIFICATE_CHECKER_UNTRUSTED && !untrustedPGPKeys.isEmpty()) {
			StringBuilder message = new StringBuilder("The following PGP Keys are untrusted:\n"); //$NON-NLS-1$
			for (PGPPublicKey untrustedKey : untrustedPGPKeys) {
				message.append(Long.toHexString(untrustedKey.getKeyID()) + "\n"); //$NON-NLS-1$
				message.append("  used by the following artifacts:\n"); //$NON-NLS-1$
				for (Entry<IArtifactDescriptor, Collection<PGPPublicKey>> entry : untrustedPGPArtifacts.entrySet()) {
					if (entry.getValue().stream().anyMatch(signer -> signer.getKeyID() == untrustedKey.getKeyID())) {
						message.append(NLS.bind("    {0}\n", entry.getKey().getArtifactKey())); //$NON-NLS-1$
					}
				}
			}
			DebugHelper.debug(DEBUG_PREFIX, message.toString());
		}

		String policy = getUnsignedContentPolicy();
		//if there is unsigned content and we should never allow it, then fail without further checking certificates
		if (!unsigned.isEmpty() && EngineActivator.UNSIGNED_FAIL.equals(policy)) {
			return new Status(IStatus.ERROR, EngineActivator.ID, NLS.bind(Messages.CertificateChecker_UnsignedNotAllowed, unsigned));
		}

		String[] details = EngineActivator.UNSIGNED_ALLOW.equals(policy) || unsigned.isEmpty() ? null
				: unsigned.entrySet().stream().map(entry -> {
					String detail = entry.getValue().toString();
					if (untrustedPGPKeys != null && !untrustedPGPKeys.isEmpty()) {
						return detail + untrustedPGPKeys.stream().map(PGPPublicKey::getKeyID).map(Long::toHexString)
								.collect(Collectors.joining(", ", " [", "]")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					}
					return detail;
				}).toArray(String[]::new);
		Certificate[][] unTrustedCertificateChains = untrustedCertificates.isEmpty() ? null
				: untrustedChain.toArray(Certificate[][]::new);
		// If there was no unsigned content, and nothing untrusted, no need to prompt.
		if (details == null && unTrustedCertificateChains == null && untrustedPGPArtifacts.isEmpty()) {
			return status;
		}

		TrustInfo trustInfo = serviceUI.getTrustInfo(unTrustedCertificateChains,
				untrustedPGPKeys,
				details);

		// If user doesn't trust unsigned content, cancel the operation
		if (!unsigned.isEmpty() && !trustInfo.trustUnsignedContent()) {
			return Status.CANCEL_STATUS;
		}

		Certificate[] trustedCertificates = trustInfo.getTrustedCertificates();
		// If we had untrusted chains and nothing was trusted, cancel the operation
		if (unTrustedCertificateChains != null && trustedCertificates == null) {
			return new Status(IStatus.CANCEL, EngineActivator.ID, Messages.CertificateChecker_CertificateRejected);
		}
		// Anything that was trusted should be removed from the untrusted list
		if (trustedCertificates != null) {
			for (Certificate trustedCertificate : trustedCertificates) {
				untrustedCertificates.remove(trustedCertificate);
			}
		}

		trustedKeySet.addAll(trustInfo.getTrustedPGPKeys());
		untrustedPGPArtifacts.values().removeIf(pgpKeys -> pgpKeys.stream().anyMatch(trustedKeySet::contains));

		// If there is still untrusted content, cancel the operation
		if (!untrustedCertificates.isEmpty() || !untrustedPGPArtifacts.isEmpty()) {
			return new Status(IStatus.CANCEL, EngineActivator.ID, Messages.CertificateChecker_CertificateRejected);
		}
		// If we should persist the trusted certificates, add them to the trust engine
		if (trustInfo.persistTrust()) {
			IStatus certifactesStatus = trustInfo.getTrustedCertificates().length == 0 ? null
					: persistTrustedCertificates(trustedCertificates);
			trustInfo.getTrustedPGPKeys().forEach(trustedKeys.get()::addKey);
			IStatus pgpStatus = trustInfo.getTrustedPGPKeys().isEmpty() ? null : persistTrustedKeys(trustedKeys.get());
			if (pgpStatus == null) {
				return certifactesStatus;
			}
			if (certifactesStatus == null) {
				return pgpStatus;
			}
			return new MultiStatus(getClass(), IStatus.OK, new IStatus[] { pgpStatus, certifactesStatus },
					pgpStatus.getMessage() + '\n' + certifactesStatus.getMessage(), null);
		}

		return status;
	}

	private IStatus persistTrustedCertificates(Certificate[] trustedCertificates) {
		if (trustedCertificates == null)
			// I'm pretty sure this would be a bug; trustedCertificates should never be null here.
			return new Status(IStatus.INFO, EngineActivator.ID, Messages.CertificateChecker_CertificateRejected);
		ServiceTracker<TrustEngine, TrustEngine> trustEngineTracker = new ServiceTracker<>(EngineActivator.getContext(), TrustEngine.class, null);
		trustEngineTracker.open();
		Object[] trustEngines = trustEngineTracker.getServices();
		try {
			if (trustEngines == null)
				return null;
			for (Certificate trustedCertificate : trustedCertificates) {
				for (Object engine : trustEngines) {
					TrustEngine trustEngine = (TrustEngine) engine;
					if (trustEngine.isReadOnly())
						continue;
					try {
						trustEngine.addTrustAnchor(trustedCertificate, trustedCertificate.toString());
						// this should mean we added an anchor successfully; continue to next certificate
						break;
					} catch (IOException e) {
						//just return an INFO so the user can proceed with the install
						return new Status(IStatus.INFO, EngineActivator.ID, Messages.CertificateChecker_KeystoreConnectionError, e);
					} catch (GeneralSecurityException e) {
						return new Status(IStatus.INFO, EngineActivator.ID, Messages.CertificateChecker_CertificateError, e);
					}
				}
			}
		} finally {
			trustEngineTracker.close();
		}
		return Status.OK_STATUS;
	}

	/**
	 * Return the policy on unsigned content.
	 */
	private String getUnsignedContentPolicy() {
		String policy = EngineActivator.getContext().getProperty(EngineActivator.PROP_UNSIGNED_POLICY);
		if (policy == null)
			policy = EngineActivator.UNSIGNED_PROMPT;
		return policy;

	}

	public void setProfile(IProfile profile) {
		this.profile = profile;
	}

	public void add(Map<IArtifactDescriptor, File> toAdd) {
		artifacts.putAll(toAdd);
	}

	public Map<PGPPublicKey, Set<Bundle>> getContributedTrustedKeys() {
		// Build the map based on fingerprints to properly avoid duplicates as we
		// accumulate the full set of keys.
		Map<ByteBuffer, Set<Bundle>> keys = new LinkedHashMap<>();

		// Load from the extension registry.
		for (IConfigurationElement extension : RegistryFactory.getRegistry()
				.getConfigurationElementsFor(EngineActivator.ID, "pgp")) { //$NON-NLS-1$
			if ("trustedKeys".equals(extension.getName())) { //$NON-NLS-1$
				String pathInBundle = extension.getAttribute("path"); //$NON-NLS-1$
				if (pathInBundle != null) {
					String name = extension.getContributor().getName();
					Stream.of(EngineActivator.getContext().getBundles())
							.filter(bundle -> bundle.getSymbolicName().equals(name)).findAny().ifPresent(bundle -> {
								URL keyURL = bundle.getEntry(pathInBundle);
								try (InputStream stream = keyURL.openStream()) {
									PGPPublicKeyStore.readPublicKeys(stream).stream().map(keyService::addKey)
											.forEach(key -> keys.computeIfAbsent(ByteBuffer.wrap(key.getFingerprint()),
													k -> new LinkedHashSet<>()).add(bundle));
								} catch (IOException e) {
									DebugHelper.debug(DEBUG_PREFIX, e.getMessage());
								}
							});
				}
			}
		}

		Map<PGPPublicKey, Set<Bundle>> result = keys.entrySet().stream()
				.collect(Collectors.toMap(entry -> keyService.getKey(entry.getKey().array()), Map.Entry::getValue));
		return result;
	}

	public PGPPublicKeyStore getPreferenceTrustedKeys() {
		PGPPublicKeyStore trustStore = new PGPPublicKeyStore();
		if (profile != null) {
			ProfileScope profileScope = new ProfileScope(agent.getService(IAgentLocation.class),
					profile.getProfileId());
			PGPPublicKeyStore
					.readPublicKeys(profileScope.getNode(EngineActivator.ID).get(TRUSTED_KEY_STORE_PROPERTY, null))
					.stream().map(keyService::addKey).forEach(trustStore::addKey);
		}
		return trustStore;
	}

	public IStatus persistTrustedKeys(PGPPublicKeyStore trustStore) {
		if (profile != null) {
			ProfileScope profileScope = new ProfileScope(agent.getService(IAgentLocation.class),
					profile.getProfileId());
			IEclipsePreferences node = profileScope.getNode(EngineActivator.ID);
			try {
				node.put(TRUSTED_KEY_STORE_PROPERTY, trustStore.toArmoredString());
				node.flush();
			} catch (IOException | BackingStoreException ex) {
				return new Status(IStatus.ERROR, EngineActivator.ID, ex.getMessage(), ex);
			}
		}
		return Status.OK_STATUS;
	}
}

