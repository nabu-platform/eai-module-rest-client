package be.nabu.eai.module.rest.client;

import java.io.IOException;
import java.util.List;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseJAXBGUIManager;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;

public class RESTEndpointGUIManager extends BaseJAXBGUIManager<RESTEndpointConfiguration, RESTEndpointArtifact> {

	public RESTEndpointGUIManager() {
		super("REST Endpoint", RESTEndpointArtifact.class, new RESTEndpointManager(), RESTEndpointConfiguration.class);
	}

	@Override
	protected List<Property<?>> getCreateProperties() {
		return null;
	}

	@Override
	protected RESTEndpointArtifact newInstance(MainController controller, RepositoryEntry entry, Value<?>... values) throws IOException {
		return new RESTEndpointArtifact(entry.getId(), entry.getContainer(), entry.getRepository());
	}

	@Override
	public String getCategory() {
		return "REST";
	}
	
}
