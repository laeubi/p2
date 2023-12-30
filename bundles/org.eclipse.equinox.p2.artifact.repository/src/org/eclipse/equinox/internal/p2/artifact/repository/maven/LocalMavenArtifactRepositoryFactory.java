package org.eclipse.equinox.internal.p2.artifact.repository.maven;

import java.io.File;
import java.net.URI;
import java.util.Map;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.internal.p2.artifact.repository.Activator;
import org.eclipse.equinox.internal.p2.artifact.repository.Messages;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.spi.ArtifactRepositoryFactory;
import org.eclipse.osgi.util.NLS;

public class LocalMavenArtifactRepositoryFactory extends ArtifactRepositoryFactory {

	@Override
	public IArtifactRepository create(URI location, String name, String type, Map<String, String> properties)
			throws ProvisionException {
		throw new ProvisionException("Create is not supported!"); //$NON-NLS-1$
	}

	@Override
	public IArtifactRepository load(URI location, int flags, IProgressMonitor monitor) throws ProvisionException {
		File baseFolder = getBaseFolder(location);
		return new LocalMavenArtifactRepository(getAgent(), baseFolder, location);
	}

	private File getBaseFolder(URI location) throws ProvisionException {
		if ("file".equals(location.getScheme())) { //$NON-NLS-1$
			File file = new File(location);
			if (file.isDirectory()) {
				return file;
			}
		}
		throw new ProvisionException(new Status(IStatus.ERROR, Activator.ID, ProvisionException.REPOSITORY_NOT_FOUND,
				NLS.bind(Messages.io_failedRead, location), null));
	}

}
