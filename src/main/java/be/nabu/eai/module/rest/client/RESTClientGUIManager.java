package be.nabu.eai.module.rest.client;

import java.io.IOException;
import java.util.List;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseJAXBGUIManager;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;

public class RESTClientGUIManager extends BaseJAXBGUIManager<RESTClientConfiguration, RESTClientArtifact> {

	public RESTClientGUIManager() {
		super("REST Client Service", RESTClientArtifact.class, new RESTClientManager(), RESTClientConfiguration.class);
	}

	@Override
	protected List<Property<?>> getCreateProperties() {
		return null;
	}

	@Override
	protected RESTClientArtifact newInstance(MainController controller, RepositoryEntry entry, Value<?>... values) throws IOException {
		return new RESTClientArtifact(entry.getId(), entry.getContainer(), entry.getRepository());
	}

	@Override
	public String getCategory() {
		return "Services";
	}
}
