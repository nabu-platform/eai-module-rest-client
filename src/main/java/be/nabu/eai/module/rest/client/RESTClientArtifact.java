/*
* Copyright (C) 2015 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.eai.module.rest.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.module.rest.RESTUtils;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.libs.artifacts.ExternalDependencyImpl;
import be.nabu.libs.artifacts.api.ExternalDependency;
import be.nabu.libs.artifacts.api.ExternalDependencyArtifact;
import be.nabu.libs.http.glue.GlueListener;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.ServiceInstance;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.properties.AliasProperty;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.structure.Structure;

public class RESTClientArtifact extends JAXBArtifact<RESTClientConfiguration> implements DefinedService, ExternalDependencyArtifact {

	private Logger logger = LoggerFactory.getLogger(getClass());
	
	private Structure input, output, query, requestHeader, responseHeader, path;
	
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
					synchronized(RESTClientArtifact.this) {
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
					synchronized(RESTClientArtifact.this) {
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
	
	public Structure getQuery() {
		if (query == null) {
			query = new Structure();
			query.setName("query");
		}
		return query;
	}
	public void setQuery(Structure query) {
		this.query = query;
	}
	public Structure getRequestHeader() {
		if (requestHeader == null) {
			requestHeader = new Structure();
			requestHeader.setName("requestHeader");
		}
		return requestHeader;
	}

	public void setRequestHeader(Structure requestHeader) {
		this.requestHeader = requestHeader;
	}

	public Structure getResponseHeader() {
		if (responseHeader == null) {
			responseHeader = new Structure();
			responseHeader.setName("responseHeader");
		}
		return responseHeader;
	}

	public void setResponseHeader(Structure responseHeader) {
		this.responseHeader = responseHeader;
	}
	

	public Structure getPath() {
		if (path == null) {
			path = new Structure();
			path.setName("path");
		}
		return path;
	}

	public void setPath(Structure path) {
		this.path = path;
	}

	private static List<String> removeAll(Structure structure) {
		return removeUnused(structure, new ArrayList<String>());
	}
	
	private static List<String> removeUnused(Structure structure, List<String> names) {
		List<String> available = new ArrayList<String>();
		List<Element<?>> allChildren = new ArrayList<Element<?>>(TypeUtils.getAllChildren(structure));
		for (Element<?> child : allChildren) {
			if (!names.contains(child.getName())) {
				structure.remove(child);
			}
			else {
				available.add(child.getName());
			}
		}
		return available;
	}
	
	private List<String> cleanup(List<String> names) {
		List<String> cleaned = new ArrayList<String>();
		for (String name : names) {
			cleaned.add(name.replaceAll("[^\\w]+", "_"));
		}
		return cleaned;
	}
	
	private List<String> toHeaderNames(List<String> names) {
		List<String> cleaned = new ArrayList<String>();
		for (String name : names) {
			cleaned.add(RESTUtils.headerToField(name));
		}
		return cleaned;
	}
	
	private void rebuildInterface() {
		Structure input = this.input == null ? new Structure() : RESTUtils.clean(this.input);
		Structure output = this.output == null ? new Structure() : RESTUtils.clean(this.output);
		Structure path = getPath();
		Structure query = getQuery();
		Structure requestHeader = getRequestHeader();
		Structure responseHeader = getResponseHeader();
		Structure authentication = new Structure();
		try {
			RESTEndpointArtifact endpoint = getConfig().getEndpoint();
			// input
			input.setName("input");
			input.add(new SimpleElementImpl<String>("transactionId", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			input.add(new SimpleElementImpl<URI>("endpoint", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(URI.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			
			if (getConfiguration().getQueryParameters() != null && !getConfiguration().getQueryParameters().trim().isEmpty()) {
				List<String> names = Arrays.asList(getConfiguration().getQueryParameters().split("[\\s,]+"));
				List<String> available = removeUnused(query, cleanup(names));
				for (String name : names) {
					String cleanupName = name.replaceAll("[^\\w]+", "_");
					if (!available.contains(cleanupName)) {
						SimpleElementImpl<String> element = new SimpleElementImpl<String>(cleanupName, SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), query, new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0));
						if (!cleanupName.equals(name)) {
							element.setProperty(new ValueImpl<String>(AliasProperty.getInstance(), name));
						}
						query.add(element);
					}
				}
				boolean required = false;
				for (Element<?> child : query) {
					Value<Integer> property = child.getProperty(MinOccursProperty.getInstance());
					if (property == null || property.getValue() > 0) {
						required = true;
						break;
					}
				}
				input.add(new ComplexElementImpl("query", query, input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), required ? 1 : 0)));
			}
			else {
				removeAll(query);
			}
			if (getConfiguration().getRequestHeaders() != null && !getConfiguration().getRequestHeaders().trim().isEmpty()) {
				List<String> names = Arrays.asList(getConfiguration().getRequestHeaders().split("[\\s,]+"));
				List<String> available = removeUnused(requestHeader, toHeaderNames(names));
				for (String name : names) {
					String cleanupName = RESTUtils.headerToField(name);
					if (!available.contains(cleanupName)) {
						SimpleElementImpl<String> element = new SimpleElementImpl<String>(cleanupName, SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), requestHeader, new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0));
						if (!cleanupName.equals(name)) {
							element.setProperty(new ValueImpl<String>(AliasProperty.getInstance(), name));
						}
						requestHeader.add(element);
					}
				}
				boolean required = false;
				for (Element<?> child : requestHeader) {
					Value<Integer> property = child.getProperty(MinOccursProperty.getInstance());
					if (property == null || property.getValue() > 0) {
						required = true;
						break;
					}
				}
				input.add(new ComplexElementImpl("header", requestHeader, input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), required ? 1 : 0)));
			}
			else {
				removeAll(requestHeader);
			}
			String pathToAnalyze = getConfig().getPath();
			// if it is not simply the root, we want to add the base path as well
			if (endpoint != null && endpoint.getConfig().getBasePath() != null && !endpoint.getConfig().getBasePath().trim().equals("/")) {
				if (pathToAnalyze == null) {
					pathToAnalyze = endpoint.getConfig().getBasePath();
				}
				else {
					// remove doubles we might have introduced with the hard concatting
					pathToAnalyze = (endpoint.getConfig().getBasePath() + "/" + pathToAnalyze).replaceAll("[/]{2,}", "/");
				}
			}
			if (pathToAnalyze != null) {
				List<String> names = GlueListener.analyzePath(pathToAnalyze).getParameters();
				List<String> available = removeUnused(path, names);
				for (String name : names) {
					if (!available.contains(name)) {
						path.add(new SimpleElementImpl<String>(name, SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), path));
					}
				}
				if (path.iterator().hasNext()) {
					input.add(new ComplexElementImpl("path", path, input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 1)));
				}
				else {
					removeAll(path);
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
			
			if (endpoint != null && endpoint.getConfig().getApiHeaderName() != null && endpoint.getConfig().getApiHeaderKey() == null) {
				input.add(new SimpleElementImpl<String>("apiHeaderKey", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input));
			}
			if (endpoint != null && endpoint.getConfig().getApiQueryName() != null && endpoint.getConfig().getApiQueryKey() == null) {
				input.add(new SimpleElementImpl<String>("apiQueryKey", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input));
			}
			
			// output
			output.setName("output");
			if (getConfiguration().getResponseHeaders() != null && !getConfiguration().getResponseHeaders().trim().isEmpty()) {
				List<String> names = Arrays.asList(getConfiguration().getResponseHeaders().split("[\\s,]+"));
				List<String> available = removeUnused(responseHeader, toHeaderNames(names));
				for (String name : names) {
					String cleanupName = RESTUtils.headerToField(name);
					if (!available.contains(cleanupName)) {
						SimpleElementImpl<String> element = new SimpleElementImpl<String>(cleanupName, SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), responseHeader);
						if (!cleanupName.equals(name)) {
							element.setProperty(new ValueImpl<String>(AliasProperty.getInstance(), name));
						}
						responseHeader.add(element);
					}
				}
				boolean required = false;
				for (Element<?> child : responseHeader) {
					Value<Integer> property = child.getProperty(MinOccursProperty.getInstance());
					if (property == null || property.getValue() > 0) {
						required = true;
						break;
					}
				}
				output.add(new ComplexElementImpl("header", responseHeader, output, new ValueImpl<Integer>(MinOccursProperty.getInstance(), required ? 1 : 0)));
			}
			else {
				removeAll(responseHeader);
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

	@Override
	public String getDescription() {
		return getConfig().getDescription();
	}

	@Override
	public List<ExternalDependency> getExternalDependencies() {
		List<ExternalDependency> dependencies = new ArrayList<ExternalDependency>();
		ExternalDependencyImpl dependency = new ExternalDependencyImpl();
		try {
			dependency.setEndpoint(new URI(
				getConfig().getSecure() != null && getConfig().getSecure() ? "https" : "http",
				// authority, this includes the port
				getConfig().getHost(),
				getConfig().getPath(),
				// query is only filled in at runtime
				null,
				// fragment is irrelevant?
				null));
		}
		catch (URISyntaxException e) {
			// can't help it...
		}
		dependency.setArtifactId(getId());
		if (getConfig().getMethod() != null) {
			dependency.setMethod(getConfig().getMethod().name().toUpperCase());
		}
		dependency.setDescription(getConfig().getDescription());
		dependency.setType("REST");
		dependency.setCredentials(getConfig().getUsername());
		dependencies.add(dependency);
		return dependencies;
	}
	
}
