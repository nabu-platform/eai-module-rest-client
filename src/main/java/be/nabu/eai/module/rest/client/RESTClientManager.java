package be.nabu.eai.module.rest.client;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;

import be.nabu.eai.module.types.structure.StructureManager;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.managers.base.JAXBArtifactManager;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.types.structure.Structure;
import be.nabu.libs.validator.api.Validation;

public class RESTClientManager extends JAXBArtifactManager<RESTClientConfiguration, RESTClientArtifact> {

	public RESTClientManager() {
		super(RESTClientArtifact.class);
	}

	@Override
	protected RESTClientArtifact newInstance(String id, ResourceContainer<?> container, Repository repository) {
		return new RESTClientArtifact(id, container, repository);
	}

	@Override
	public RESTClientArtifact load(ResourceEntry entry, List<Validation<?>> messages) throws IOException, ParseException {
		RESTClientArtifact artifact = super.load(entry, messages);
		artifact.setQuery(loadIfExists(entry, "input-query.xml"));
		artifact.setRequestHeader(loadIfExists(entry, "input-request-header.xml"));
		artifact.setPath(loadIfExists(entry, "input-path.xml"));
		artifact.setResponseHeader(loadIfExists(entry, "input-response-header.xml"));
		return artifact;
	}
	
	@Override
	public List<Validation<?>> save(ResourceEntry entry, RESTClientArtifact artifact) throws IOException {
		List<Validation<?>> messages = super.save(entry, artifact);
		StructureManager.format(entry, artifact.getQuery(), "input-query.xml");
		StructureManager.format(entry, artifact.getRequestHeader(), "input-request-header.xml");
		StructureManager.format(entry, artifact.getPath(), "input-path.xml");
		StructureManager.format(entry, artifact.getResponseHeader(), "input-response-header.xml");
		return messages;
	}
	
	private static Structure loadIfExists(ResourceEntry entry, String name) {
		Resource child = entry.getContainer().getChild(name);
		if (child instanceof ReadableResource) {
			try {
				return StructureManager.parse(entry, name);
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return null;
	}
}
