package be.nabu.eai.module.rest.client;

import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.managers.base.JAXBArtifactManager;
import be.nabu.libs.resources.api.ResourceContainer;

public class RESTClientManager extends JAXBArtifactManager<RESTClientConfiguration, RESTClientArtifact> {

	public RESTClientManager() {
		super(RESTClientArtifact.class);
	}

	@Override
	protected RESTClientArtifact newInstance(String id, ResourceContainer<?> container, Repository repository) {
		return new RESTClientArtifact(id, container);
	}

}
