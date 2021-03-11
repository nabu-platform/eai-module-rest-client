package be.nabu.eai.module.rest.client;

import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.managers.base.JAXBArtifactManager;
import be.nabu.libs.resources.api.ResourceContainer;

public class RESTEndpointManager extends JAXBArtifactManager<RESTEndpointConfiguration, RESTEndpointArtifact> {

	public RESTEndpointManager() {
		super(RESTEndpointArtifact.class);
	}

	@Override
	protected RESTEndpointArtifact newInstance(String id, ResourceContainer<?> container, Repository repository) {
		return new RESTEndpointArtifact(id, container, repository);
	}

}
