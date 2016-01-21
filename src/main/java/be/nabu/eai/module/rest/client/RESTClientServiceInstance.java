package be.nabu.eai.module.rest.client;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;

import nabu.utils.Http;
import be.nabu.eai.repository.artifacts.web.rest.WebResponseType;
import be.nabu.eai.repository.artifacts.web.rest.WebRestArtifact;
import be.nabu.libs.authentication.api.principals.BasicPrincipal;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.client.BasicAuthentication;
import be.nabu.libs.http.client.DefaultHTTPClient;
import be.nabu.libs.http.core.DefaultHTTPRequest;
import be.nabu.libs.http.core.HTTPUtils;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.api.ServiceInstance;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.binding.api.MarshallableBinding;
import be.nabu.libs.types.binding.api.UnmarshallableBinding;
import be.nabu.libs.types.binding.api.Window;
import be.nabu.libs.types.binding.form.FormBinding;
import be.nabu.libs.types.binding.json.JSONBinding;
import be.nabu.libs.types.binding.xml.XMLBinding;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.mime.api.ContentPart;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.api.ModifiablePart;
import be.nabu.utils.mime.impl.MimeHeader;
import be.nabu.utils.mime.impl.MimeUtils;
import be.nabu.utils.mime.impl.PlainMimeContentPart;
import be.nabu.utils.mime.impl.PlainMimeEmptyPart;

public class RESTClientServiceInstance implements ServiceInstance {

	private RESTClientArtifact artifact;

	public RESTClientServiceInstance(RESTClientArtifact restClientArtifact) {
		this.artifact = restClientArtifact;
	}

	@Override
	public Service getDefinition() {
		return artifact;
	}

	@SuppressWarnings("unchecked")
	@Override
	public ComplexContent execute(ExecutionContext executionContext, ComplexContent input) throws ServiceException {
		try {
			if (artifact.getConfiguration().getHost() == null) {
				throw new ServiceException("REST-CLIENT-1", "No host configured for: " + artifact.getId());
			}
			if (artifact.getConfiguration().getPath() == null) {
				throw new ServiceException("REST-CLIENT-2", "No path configured for: " + artifact.getId());
			}
			Object object = input.get("content");
			ModifiablePart part;
			Charset charset = artifact.getConfiguration().getCharset() != null ? Charset.forName(artifact.getConfiguration().getCharset()) : Charset.defaultCharset();
			if (object instanceof InputStream) {
				part = new PlainMimeContentPart(null, IOUtils.wrap((InputStream) object),
					new MimeHeader("Content-Type", "application/octet-stream"),
					new MimeHeader("Transfer-Encoding", "Chunked")
				);
			}
			else if (object instanceof ComplexContent) {
				MarshallableBinding binding;
				switch(artifact.getConfiguration().getRequestType()) {
					case FORM_ENCODED: binding = new FormBinding(((ComplexContent) object).getType()); break;
					case JSON: binding = new JSONBinding(((ComplexContent) object).getType(), charset); break;
					default: 
						XMLBinding xmlBinding = new XMLBinding(((ComplexContent) object).getType(), charset);
						// we had an instance where the other party was using dashes in the names
						// it is impossible to model a field name with a dash in it in nabu so it is (currently) acceptable to set this boolean globally
						// if we ever do need to support dashes, make this configurable, i didn't want to do that at the time of writing because not all bindings support it (atm)
						xmlBinding.setCamelCaseDashes(true);
						binding = xmlBinding;
				}
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				binding.marshal(output, (ComplexContent) object);
				byte [] content = output.toByteArray();
				part = new PlainMimeContentPart(null, IOUtils.wrap(content, true), 
					new MimeHeader("Content-Length", Integer.valueOf(content.length).toString()),
					new MimeHeader("Content-Type", artifact.getConfiguration().getRequestType().getMimeType())
				);
			}
			else if (object == null) {
				part = new PlainMimeEmptyPart(null);
			}
			else {
				throw new ServiceException("REST-CLIENT-2", "Invalid content");
			}
			
			part.setHeader(new MimeHeader("Accept", WebResponseType.XML.equals(artifact.getConfiguration().getRequestType()) ? "application/xml" : "application/json"));
			
			Object header = input.get("header");
			if (header instanceof ComplexContent) {
				for (Element<?> element : ((ComplexContent) header).getType()) {
					Object values = ((ComplexContent) header).get(element.getName());
					if (values instanceof Collection) {
						for (Object value : (Collection<?>) values) {
							if (value != null) {
								part.setHeader(new MimeHeader(WebRestArtifact.fieldToHeader(element.getName()), value.toString()));
							}
						}
					}
				}
			}

			if (artifact.getConfiguration().getGzip() != null && artifact.getConfiguration().getGzip()) {
				part.setHeader(new MimeHeader("Content-Encoding", "gzip"));
				part.setHeader(new MimeHeader("Accept-Encoding", "gzip"));
			}
			part.setHeader(new MimeHeader("Host", artifact.getConfiguration().getHost()));
			
			final String username = input == null || input.get("authentication/username") == null ? artifact.getConfiguration().getUsername() : (String) input.get("authentication/username");
			final String password = input == null || input.get("authentication/password") == null ? artifact.getConfiguration().getPassword() : (String) input.get("authentication/password");
			
			BasicPrincipal principal = artifact.getConfiguration().getUsername() == null ? null : new BasicPrincipal() {
				private static final long serialVersionUID = 1L;
				@Override
				public String getName() {
					return username;
				}
				@Override
				public String getPassword() {
					return password;
				}
			};
			
			String path;
			if (artifact.getConfiguration().getPath() == null) {
				path = "/";
			}
			else {
				path = artifact.getConfiguration().getPath();
				ComplexContent pathContent = (ComplexContent) input.get("path");
				if (pathContent != null) {
					for (Element<?> child : pathContent.getType()) {
						Object value = pathContent.get(child.getName());
						path = path.replaceAll("\\{[\\s]*" + child.getName() + "\\b[^}]*\\}", value == null ? "" : value.toString());
					}
				}
				if (!path.startsWith("/")) {
					path = "/" + path;
				}
			}
			
			ComplexContent queryContent = (ComplexContent) input.get("query");
			if (queryContent != null) {
				boolean first = true;
				for (Element<?> element : queryContent.getType()) {
					List<String> values = (List<String>) queryContent.get(element.getName());
					if (values != null && !values.isEmpty()) {
						for (String value : values) {
							if (first) {
								first = false;
								path += "?";
							}
							else {
								path += "&";
							}
							path += element.getName() + "=" + value.replace("&", "&amp;");
						}
					}
				}
			}
			HTTPRequest request = new DefaultHTTPRequest(
				artifact.getConfiguration().getMethod() == null ? "GET" : artifact.getConfiguration().getMethod().toString(),
				path,
				part);
			
			if (artifact.getConfiguration().getPreemptiveAuthorizationType() != null) {
				switch(artifact.getConfiguration().getPreemptiveAuthorizationType()) {
					case BASIC:
						request.getContent().setHeader(new MimeHeader(HTTPUtils.SERVER_AUTHENTICATE_RESPONSE, new BasicAuthentication().authenticate(principal, "basic")));
					break;
					case BEARER:
						request.getContent().setHeader(new MimeHeader(HTTPUtils.SERVER_AUTHENTICATE_RESPONSE, "Bearer " + principal.getName()));
					break;
				}
			}
			
			boolean isSecure = artifact.getConfiguration().getSecure() != null && artifact.getConfiguration().getSecure();
			DefaultHTTPClient client = Http.getTransactionable(executionContext, (String) input.get("transactionId"), artifact.getConfiguration().getHttpClient()).getClient();
			HTTPResponse response = client.execute(request, principal, isSecure, true);
			
			if (response.getCode() < 200 || response.getCode() >= 300) {
				byte[] content = null;
				if (response.getContent() instanceof ContentPart) {
					ReadableContainer<ByteBuffer> readable = ((ContentPart) response.getContent()).getReadable();
					if (readable != null) {
						try {
							content = IOUtils.toBytes(readable);
						}
						finally {
							readable.close();
						}
					}
				}
				throw new ServiceException("REST-CLIENT-3", "An error occurred on the remote server: [" + response.getCode() + "] " + response.getMessage() + (content == null ? "" : "\n" + new String(content)));
			}
			
			ComplexContent output = artifact.getServiceInterface().getOutputDefinition().newInstance();
			if (response.getContent() != null) {
				String responseContentType = MimeUtils.getContentType(response.getContent().getHeaders());
				
				if (response.getContent() instanceof ContentPart) {
					if (artifact.getConfiguration().getOutputAsStream() != null && artifact.getConfiguration().getOutputAsStream()) {
						output.set("content", IOUtils.toInputStream(((ContentPart) response.getContent()).getReadable()));
					}
					else if (artifact.getConfiguration().getOutput() != null) {
						if (responseContentType == null && artifact.getConfiguration().getRequestType() != null) {
							responseContentType = artifact.getConfiguration().getRequestType().getMimeType();
						}
						UnmarshallableBinding binding;
						if ("application/x-www-form-urlencoded".equalsIgnoreCase(responseContentType)) {
							binding = new FormBinding((ComplexType) artifact.getConfiguration().getOutput(), charset);
						}
						else if ("application/json".equalsIgnoreCase(responseContentType) || "application/javascript".equalsIgnoreCase(responseContentType) || "application/x-javascript".equalsIgnoreCase(responseContentType)) {
							binding = new JSONBinding((ComplexType) artifact.getConfiguration().getOutput(), charset);
						}
						else {
							XMLBinding xmlBinding = new XMLBinding((ComplexType) artifact.getConfiguration().getOutput(), charset);
							// cfr
							xmlBinding.setCamelCaseDashes(true);
							binding = xmlBinding;
						}
						output.set("content", binding.unmarshal(IOUtils.toInputStream(((ContentPart) response.getContent()).getReadable()), new Window[0]));
					}
				}
				Element<?> element = output.getType().get("header");
				if (element != null) {
					for (Element<?> child : (ComplexType) element.getType()) {
						Header[] headers = MimeUtils.getHeaders(WebRestArtifact.fieldToHeader(child.getName()), response.getContent().getHeaders());
						if (headers != null) {
							for (int i = 0; i < headers.length; i++) {
								output.set("header/" + child.getName() + "[" + i + "]", headers[i].getValue());
							}
						}
					}
				}
			}
			return output;
		}
		catch (Exception e) {
			throw new ServiceException(e);
		}
	}

}