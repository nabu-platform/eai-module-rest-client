package be.nabu.eai.module.rest.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.eai.repository.artifacts.web.rest.WebRestArtifact;
import be.nabu.libs.http.glue.GlueListener;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.ServiceInstance;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.structure.Structure;

public class RESTClientArtifact extends JAXBArtifact<RESTClientConfiguration> implements DefinedService {

	private Logger logger = LoggerFactory.getLogger(getClass());
	
	private Structure input, output;
	
	public RESTClientArtifact(String id, ResourceContainer<?> directory, Repository repository) {
		super(id, directory, repository, "rest-client.xml", RESTClientConfiguration.class);
	}

	@Override
	public void save(ResourceContainer<?> directory) throws IOException {
		synchronized(this) {
			rebuildInterface();
		}
		super.save(directory);
	}
	
	@Override
	public ServiceInterface getServiceInterface() {
		return new ServiceInterface() {
			@Override
			public ComplexType getInputDefinition() {
				if (input == null) {
					synchronized(this) {
						if (input == null) {
							rebuildInterface();
						}
					}
				}
				return input;
			}
			@Override
			public ComplexType getOutputDefinition() {
				if (output == null) {
					synchronized(this) {
						if (output == null) {
							rebuildInterface();
						}
					}
				}
				return output;
			}
			@Override
			public ServiceInterface getParent() {
				return null;
			}
		};
	}

	@Override
	public ServiceInstance newInstance() {
		return new RESTClientServiceInstance(this);
	}

	@Override
	public Set<String> getReferences() {
		return new HashSet<String>();
	}
	
	private void rebuildInterface() {
		Structure input = this.input == null ? new Structure() : WebRestArtifact.clean(this.input);
		Structure output = this.output == null ? new Structure() : WebRestArtifact.clean(this.output);
		Structure path = new Structure();
		Structure query = new Structure();
		Structure requestHeader = new Structure();
		Structure responseHeader = new Structure();
		Structure authentication = new Structure();
		try {
			// input
			input.setName("input");
			input.add(new SimpleElementImpl<String>("transactionId", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			
			if (getConfiguration().getQueryParameters() != null && !getConfiguration().getQueryParameters().trim().isEmpty()) {
				for (String name : getConfiguration().getQueryParameters().split("[\\s,]+")) {
					query.add(new SimpleElementImpl<String>(name, SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), query, new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0)));
				}
				input.add(new ComplexElementImpl("query", query, input));
			}
			if (getConfiguration().getRequestHeaders() != null && !getConfiguration().getRequestHeaders().trim().isEmpty()) {
				for (String name : getConfiguration().getRequestHeaders().split("[\\s,]+")) {
					requestHeader.add(new SimpleElementImpl<String>(WebRestArtifact.headerToField(name), SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), requestHeader, new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0)));
				}
				input.add(new ComplexElementImpl("header", requestHeader, input));
			}
			if (getConfiguration().getPath() != null) {
				for (String name : GlueListener.analyzePath(getConfiguration().getPath()).getParameters()) {
					path.add(new SimpleElementImpl<String>(name, SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), path));
				}
				if (path.iterator().hasNext()) {
					input.add(new ComplexElementImpl("path", path, input));
				}
			}
			if (getConfiguration().getInputAsStream() != null && getConfiguration().getInputAsStream()) {
				input.add(new SimpleElementImpl<InputStream>("content", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(InputStream.class), input));
			}
			else if (getConfiguration().getInput() != null) {
				input.add(new ComplexElementImpl("content", (ComplexType) getConfiguration().getInput(), input));
			}
			
			authentication.add(new SimpleElementImpl<String>("username", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), authentication, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			authentication.add(new SimpleElementImpl<String>("password", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), authentication, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			input.add(new ComplexElementImpl("authentication", authentication, input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			
			// output
			output.setName("output");
			if (getConfiguration().getResponseHeaders() != null && !getConfiguration().getResponseHeaders().trim().isEmpty()) {
				for (String name : getConfiguration().getResponseHeaders().split("[\\s,]+")) {
					responseHeader.add(new SimpleElementImpl<String>(WebRestArtifact.headerToField(name), SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), responseHeader));
				}
				output.add(new ComplexElementImpl("header", responseHeader, output));
			}
			if (getConfiguration().getOutputAsStream() != null && getConfiguration().getOutputAsStream()) {
				output.add(new SimpleElementImpl<InputStream>("content", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(InputStream.class), output));
			}
			else if (getConfiguration().getOutput() != null) {
				output.add(new ComplexElementImpl("content", (ComplexType) getConfiguration().getOutput(), output));
			}
			
			this.input = input;
			this.output = output;
		}
		catch (Exception e) {
			logger.error("Could not build interface for " + getId(), e);
		}
	}

}
