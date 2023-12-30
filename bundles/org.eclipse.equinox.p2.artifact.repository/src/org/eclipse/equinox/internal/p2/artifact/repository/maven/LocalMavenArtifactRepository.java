package org.eclipse.equinox.internal.p2.artifact.repository.maven;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.query.*;
import org.eclipse.equinox.p2.repository.artifact.*;
import org.eclipse.equinox.p2.repository.artifact.spi.AbstractArtifactRepository;

public class LocalMavenArtifactRepository extends AbstractArtifactRepository implements IFileArtifactRepository {

	private static final CollectionResult<IArtifactKey> EMPTY_QUERY_RESULT = new CollectionResult<>(List.of());
	private static final IQueryable<IArtifactDescriptor> EMPTY_DESCRIPTOR_QUERYABLE = new CollectionResult<>(List.of());
	private static final String MAVEN = "maven"; //$NON-NLS-1$

	public LocalMavenArtifactRepository(IProvisioningAgent agent, File baseFolder, URI location) {
		super(agent, baseFolder.getAbsolutePath(), MAVEN, "2.0", location, null, MAVEN, Map.of()); //$NON-NLS-1$
	}

	@Override
	public IStatus getRawArtifact(IArtifactDescriptor descriptor, OutputStream destination, IProgressMonitor monitor) {
		File file = getArtifactFile(descriptor);
		if (file != null) {
			try {
				Files.copy(file.toPath(), destination);
			} catch (IOException e) {
				return Status.error("copy artifact to stream failed", e); //$NON-NLS-1$
			}
		}
		return Status.CANCEL_STATUS;
	}

	@Override
	public File getArtifactFile(IArtifactDescriptor descriptor) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IStatus getArtifact(IArtifactDescriptor descriptor, OutputStream destination, IProgressMonitor monitor) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IStatus getArtifacts(IArtifactRequest[] requests, IProgressMonitor monitor) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public OutputStream getOutputStream(IArtifactDescriptor descriptor) throws ProvisionException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IArtifactDescriptor[] getArtifactDescriptors(IArtifactKey key) {
		return new IArtifactDescriptor[0];
	}

	@Override
	public IQueryable<IArtifactDescriptor> descriptorQueryable() {
		return EMPTY_DESCRIPTOR_QUERYABLE;
	}

	@Override
	public IQueryResult<IArtifactKey> query(IQuery<IArtifactKey> query, IProgressMonitor monitor) {
		return EMPTY_QUERY_RESULT;
	}

	@Override
	public File getArtifactFile(IArtifactKey key) {
		// we can't get the file from a key only...
		return null;
	}

	@Override
	public boolean contains(IArtifactDescriptor descriptor) {
		return getArtifactFile(descriptor) != null;
	}

	@Override
	public boolean contains(IArtifactKey key) {
		return getArtifactFile(key) != null;
	}

}
