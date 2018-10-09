package be.nabu.eai.module.rest.client;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;

import nabu.protocols.http.client.Services;
import be.nabu.eai.module.rest.RESTUtils;
import be.nabu.eai.module.rest.WebResponseType;
import be.nabu.libs.authentication.api.principals.BasicPrincipal;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.api.client.HTTPClient;
import be.nabu.libs.http.client.BasicAuthentication;
import be.nabu.libs.http.client.NTLMPrincipalImpl;
import be.nabu.libs.http.core.DefaultHTTPRequest;
import be.nabu.libs.http.core.HTTPUtils;
import be.nabu.libs.http.glue.GlueListener;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.api.ServiceInstance;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.base.CollectionFormat;
import be.nabu.libs.types.binding.api.MarshallableBinding;
import be.nabu.libs.types.binding.api.UnmarshallableBinding;
import be.nabu.libs.types.binding.api.Window;
import be.nabu.libs.types.binding.form.FormBinding;
import be.nabu.libs.types.binding.json.JSONBinding;
import be.nabu.libs.types.binding.xml.XMLBinding;
import be.nabu.libs.types.map.MapTypeGenerator;
import be.nabu.libs.types.properties.AliasProperty;
import be.nabu.libs.types.properties.CollectionFormatProperty;
import be.nabu.libs.validator.api.Validator;
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

	private String escapeQuery(String value) {
		return URIUtils.encodeURL(value);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public ComplexContent execute(ExecutionContext executionContext, ComplexContent input) throws ServiceException {
		try {
			if (artifact.getConfiguration().getHost() == null) {
				throw new ServiceException("REST-CLIENT-1", "No host configured for: " + artifact.getId());
			}
			if (artifact.getConfiguration().getPath() == null) {
				throw new ServiceException("REST-CLIENT-2", "No path configured for: " + artifact.getId());
			}
			Object object = input == null ? null : input.get("content");
			URI uri = input == null ? null : (URI) input.get("endpoint");
			ModifiablePart part;
			Charset charset = artifact.getConfiguration().getCharset() != null ? Charset.forName(artifact.getConfiguration().getCharset()) : Charset.defaultCharset();
			WebResponseType requestType = artifact.getConfiguration().getRequestType();
			if (object instanceof InputStream) {
				part = new PlainMimeContentPart(null, IOUtils.wrap((InputStream) object),
					new MimeHeader("Content-Type", requestType == null ? "application/octet-stream" : requestType.getMimeType()),
					new MimeHeader("Transfer-Encoding", "Chunked")
				);
			}
			else if (object instanceof ComplexContent) {
				if (requestType == null) {
					requestType = WebResponseType.XML;
				}
				if (artifact.getConfig().getValidateInput() != null && artifact.getConfig().getValidateInput()) {
					Validator validator = ((ComplexContent) object).getType().createValidator();
					List validations = validator.validate(object);
					if (validations != null && !validations.isEmpty()) {
						throw new ServiceException("REST-CLIENT-5", "The input provided to the rest client is invalid: " + validations);
					}
				}
				MarshallableBinding binding;
				switch(requestType) {
					case FORM_ENCODED: binding = new FormBinding(((ComplexContent) object).getType()); break;
					case JSON: 
						JSONBinding jsonBinding = new JSONBinding(((ComplexContent) object).getType(), charset);
						// TODO: make this configurable?
						jsonBinding.setIgnoreRootIfArrayWrapper(true);
						// see below in XML binding
						jsonBinding.setCamelCaseDashes(true);
						binding = jsonBinding;
					break;
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
					new MimeHeader("Content-Type", requestType.getMimeType())
				);
			}
			else if (object == null) {
				part = new PlainMimeEmptyPart(null);
			}
			else {
				throw new ServiceException("REST-CLIENT-3", "Invalid content");
			}
			
			WebResponseType responseType = artifact.getConfiguration().getResponseType();
			if (responseType == null) {
				responseType = WebResponseType.XML;
			}
			part.setHeader(new MimeHeader("Accept", responseType.getMimeType()));
			
			Object header = input == null ? null : input.get("header");
			if (header instanceof ComplexContent) {
				for (Element<?> element : ((ComplexContent) header).getType()) {
					Object values = ((ComplexContent) header).get(element.getName());
					if (values instanceof Collection) {
						for (Object value : (Collection<?>) values) {
							if (value != null) {
								part.setHeader(new MimeHeader(RESTUtils.fieldToHeader(element.getName()), value.toString()));
							}
						}
					}
				}
			}

			if (artifact.getConfiguration().getGzip() != null && artifact.getConfiguration().getGzip()) {
				part.setHeader(new MimeHeader("Content-Encoding", "gzip"));
				part.setHeader(new MimeHeader("Accept-Encoding", "gzip"));
			}
			part.setHeader(new MimeHeader("Host", uri == null || uri.getHost() == null ? artifact.getConfiguration().getHost() : uri.getAuthority()));
			
			final String username = input == null || input.get("authentication/username") == null ? artifact.getConfiguration().getUsername() : (String) input.get("authentication/username");
			final String password = input == null || input.get("authentication/password") == null ? artifact.getConfiguration().getPassword() : (String) input.get("authentication/password");

			// currently if preemptive is filled in, you can't do ntlm, only basic & bearer both of which don't support domain
			boolean allowNtlm = artifact.getConfiguration().getPreemptiveAuthorizationType() == null;
			
			BasicPrincipal principal = null;
			if (username != null) {
				int index = username.indexOf('/');
				if (index < 0) {
					index = username.indexOf('\\');
				}
				if (index < 0 || !allowNtlm) {
					principal = new BasicPrincipal() {
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
				}
				// create an NTLM principal
				else if (username != null) {
					principal = new NTLMPrincipalImpl(username.substring(0, index), username.substring(index + 1), password);
				}
			}
			
			String path = uri == null || uri.getPath() == null ? "/" : uri.getPath();
			if (artifact.getConfiguration().getPath() != null) {
				String configuredPath = artifact.getConfig().getPath();
				if (configuredPath.startsWith("/")) {
					configuredPath = configuredPath.substring(1);
				}
				if (!path.endsWith("/")) {
					path += "/";
				}
				path += configuredPath;
				ComplexContent pathContent = input == null ? null : (ComplexContent) input.get("path");
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
			
			ComplexContent queryContent = input == null ? null : (ComplexContent) input.get("query");
			if (queryContent != null) {
				boolean first = path.indexOf('?') < 0;
				for (Element<?> element : queryContent.getType()) {
					Object value = queryContent.get(element.getName());
					
					if (value != null) {
						String name = ValueUtils.getValue(AliasProperty.getInstance(), element.getProperties());
						if (name == null) {
							name = element.getName();
						}
						if (value instanceof Iterable) {
							CollectionFormat collectionFormat = ValueUtils.getValue(CollectionFormatProperty.getInstance(), element.getProperties());
							boolean firstValue = true;
							for (Object single : (Iterable<?>) value) {
								if (single == null) {
									continue;
								}
								if (firstValue) {
									if (first) {
										first = false;
										path += "?";
									}
									else {
										path += "&";
									}
									path += escapeQuery(name) + "=";
									// if it is multi, we want to keep appending the values as key=value
									firstValue = CollectionFormat.MULTI.equals(collectionFormat);
								}
								else {
									path += (collectionFormat == null ? CollectionFormat.CSV : collectionFormat).getCharacter();
								}
								path += escapeQuery(single instanceof String ? single.toString() : ConverterFactory.getInstance().getConverter().convert(single, String.class));
							}
						}
						else {
							if (first) {
								first = false;
								path += "?";
							}
							else {
								path += "&";
							}
							path += escapeQuery(name) + "=" + escapeQuery(value instanceof String ? value.toString() : ConverterFactory.getInstance().getConverter().convert(value, String.class));
						}
					}
				}
			}
			HTTPRequest request = new DefaultHTTPRequest(
				artifact.getConfiguration().getMethod() == null ? "GET" : artifact.getConfiguration().getMethod().toString(),
				path,
				part);
			
			if (artifact.getConfiguration().getPreemptiveAuthorizationType() != null && principal != null) {
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
			HTTPClient client = Services.getTransactionable(executionContext, input == null ? null : (String) input.get("transactionId"), artifact.getConfiguration().getHttpClient()).getClient();
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
				throw new ServiceException("REST-CLIENT-4", "An error occurred on the remote server: [" + response.getCode() + "] " + response.getMessage() + (content == null ? "" : "\n" + new String(content)));
			}
			
			ComplexContent output = artifact.getServiceInterface().getOutputDefinition().newInstance();
			if (response.getContent() != null) {
				String responseContentType = artifact.getConfig().getResponseType() == null ? MimeUtils.getContentType(response.getContent().getHeaders()) : artifact.getConfig().getResponseType().getMimeType();
				if (response.getContent() instanceof ContentPart) {
					if (artifact.getConfiguration().getOutputAsStream() != null && artifact.getConfiguration().getOutputAsStream()) {
						output.set("content", IOUtils.toInputStream(((ContentPart) response.getContent()).getReadable()));
					}
					else if (artifact.getConfiguration().getOutput() != null) {
						if (responseContentType == null && requestType != null) {
							responseContentType = requestType.getMimeType();
						}
						UnmarshallableBinding binding;
						if ("application/x-www-form-urlencoded".equalsIgnoreCase(responseContentType)) {
							binding = new FormBinding((ComplexType) artifact.getConfiguration().getOutput(), charset);
						}
						else if ("application/json".equalsIgnoreCase(responseContentType) || "application/javascript".equalsIgnoreCase(responseContentType) || "application/x-javascript".equalsIgnoreCase(responseContentType)) {
							JSONBinding jsonBinding = new JSONBinding((ComplexType) artifact.getConfiguration().getOutput(), charset);
							// cfr
							jsonBinding.setIgnoreRootIfArrayWrapper(true);
							jsonBinding.setCamelCaseDashes(true);
							// we allow dynamic types to be generated for parsing reasons but do not allow them to be added back into the type
							// this necessitates that there is a key value array that can hold them
							jsonBinding.setAllowDynamicElements(true);
							jsonBinding.setComplexTypeGenerator(new MapTypeGenerator());
							if (artifact.getConfig().isLenient()) {
								jsonBinding.setIgnoreUnknownElements(true);
							}
							binding = jsonBinding;
						}
						else {
							XMLBinding xmlBinding = new XMLBinding((ComplexType) artifact.getConfiguration().getOutput(), charset);
							// cfr
							xmlBinding.setCamelCaseDashes(true);
							if (artifact.getConfig().isLenient()) {
								xmlBinding.setIgnoreUndefined(true);
							}
							binding = xmlBinding;
						}
						ComplexContent unmarshal = binding.unmarshal(IOUtils.toInputStream(((ContentPart) response.getContent()).getReadable()), new Window[0]);
						if (artifact.getConfig().getValidateOutput() != null && artifact.getConfig().getValidateOutput()) {
							Validator validator = unmarshal.getType().createValidator();
							List validations = validator.validate(unmarshal);
							if (validations != null && !validations.isEmpty()) {
								throw new ServiceException("REST-CLIENT-6", "The returned content from the server is invalid: " + validations);
							}
						}
						if (artifact.getConfiguration().getSanitizeOutput() != null && artifact.getConfiguration().getSanitizeOutput()) {
							unmarshal = (ComplexContent) GlueListener.sanitize(unmarshal);
						}
						output.set("content", unmarshal);
					}
				}
				Element<?> element = output.getType().get("header");
				if (element != null) {
					for (Element<?> child : (ComplexType) element.getType()) {
						Header[] headers = MimeUtils.getHeaders(RESTUtils.fieldToHeader(child.getName()), response.getContent().getHeaders());
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
