package be.nabu.eai.module.rest.client;

import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.libs.resources.api.ResourceContainer;

public class RESTEndpointArtifact extends JAXBArtifact<RESTEndpointConfiguration> {

	public RESTEndpointArtifact(String id, ResourceContainer<?> directory, Repository repository) {
		super(id, directory, repository, "rest-endpoint.xml", RESTEndpointConfiguration.class);
	}

}
