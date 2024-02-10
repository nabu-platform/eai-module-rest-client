package be.nabu.eai.module.rest.client;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.ws.rs.NotSupportedException;

import nabu.protocols.http.client.Services;
import be.nabu.eai.module.rest.RESTUtils;
import be.nabu.eai.module.rest.WebResponseType;
import be.nabu.libs.authentication.api.principals.BasicPrincipal;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.api.WebAuthorizationType;
import be.nabu.libs.http.api.client.HTTPClient;
import be.nabu.libs.http.client.BasicAuthentication;
import be.nabu.libs.http.client.NTLMPrincipalImpl;
import be.nabu.libs.http.core.DefaultHTTPRequest;
import be.nabu.libs.http.core.HTTPRequestAuthenticatorFactory;
import be.nabu.libs.http.core.HTTPUtils;
import be.nabu.libs.http.glue.GlueListener;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.api.ServiceInstance;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.Marshallable;
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
			Object object = input == null ? null : input.get("content");
			URI uri = input == null ? null : (URI) input.get("endpoint");
			
			RESTEndpointArtifact endpoint = artifact.getConfig().getEndpoint();
			
			if (artifact.getConfiguration().getHost() == null && uri == null && (endpoint == null || endpoint.getConfig().getHost() == null)) {
				throw new ServiceException("REST-CLIENT-1", "No host configured for: " + artifact.getId());
			}
			if (artifact.getConfiguration().getPath() == null && uri == null) {
				throw new ServiceException("REST-CLIENT-2", "No path configured for: " + artifact.getId());
			}
			ModifiablePart part;
			Charset charset = artifact.getConfiguration().getCharset() != null ? Charset.forName(artifact.getConfiguration().getCharset()) : 
				(endpoint == null || endpoint.getConfig().getCharset() == null ? Charset.defaultCharset() : endpoint.getConfig().getCharset());
			
			WebResponseType requestType = artifact.getConfiguration().getRequestType();
			if (requestType == null && endpoint != null) {
				requestType = endpoint.getConfig().getRequestType();
			}
			
			if (object instanceof InputStream) {
				part = new PlainMimeContentPart(null, IOUtils.wrap((InputStream) object),
						// @2023-07-04 used to be: requestType == null ? "application/octet-stream" : requestType.getMimeType()
						// The problem is, if you are using input streams, it is likely you are _not_ sending the default "json" or "xml" stuff. If you are, you can always add an explicit content-type header
					new MimeHeader("Content-Type", "application/octet-stream"),
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
						jsonBinding.setIgnoreRootIfArrayWrapper(artifact.getConfig().isIgnoreRootIfArrayWrapper());
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
				// reopenable parts can be reported on
				((PlainMimeContentPart) part).setReopenable(true);
			}
			else if (object == null) {
				part = new PlainMimeEmptyPart(null, new MimeHeader("Content-Length", "0"));
			}
			else {
				throw new ServiceException("REST-CLIENT-3", "Invalid content");
			}
			
			WebResponseType responseType = artifact.getConfiguration().getResponseType();
			if (responseType == null && endpoint != null) {
				responseType = endpoint.getConfig().getResponseType();
			}
			if (responseType == null) {
				responseType = WebResponseType.XML;
			}
			part.setHeader(new MimeHeader("Accept", responseType.getMimeType()));
			
			Object header = input == null ? null : input.get("header");
			if (header instanceof ComplexContent) {
				for (Element<?> element : TypeUtils.getAllChildren(((ComplexContent) header).getType())) {
					String alias = ValueUtils.getValue(AliasProperty.getInstance(), element.getProperties());
					if (alias == null) {
						alias = RESTUtils.fieldToHeader(element.getName());
					}
					// remove previously set content type headers
					if (alias.equalsIgnoreCase("Content-Type")) {
						part.removeHeader(alias);
					}
					Object values = ((ComplexContent) header).get(element.getName());
					if (values instanceof Collection) {
						for (Object value : (Collection<?>) values) {
							if (value != null) {
								part.setHeader(new MimeHeader(alias, ConverterFactory.getInstance().getConverter().convert(value, String.class)));
							}
						}
					}
					else {
						part.setHeader(new MimeHeader(alias, ConverterFactory.getInstance().getConverter().convert(values, String.class)));
					}
					// if we pass in a content length at the application level, unset any transfer encoding
					// note that it can get re-added if gzip is toggled
					if (alias.equalsIgnoreCase("Content-Length") && values != null) {
						part.removeHeader("Transfer-Encoding");
					}
				}
			}

			boolean gzip = false;
			if (artifact.getConfiguration().getGzip() != null) {
				gzip = artifact.getConfiguration().getGzip();
			}
			else if (endpoint != null && endpoint.getConfig().getGzip() != null) {
				gzip = endpoint.getConfig().getGzip();
			}
			if (gzip) {
				Long contentLength = MimeUtils.getContentLength(part.getHeaders());
				// if we don't have a content length, we are already chunking and want to gzip as well
				// if we have a content length and it is non-zero, we have content that needs gzipping as well
				if (contentLength == null || contentLength > 0) {
					part.setHeader(new MimeHeader("Content-Encoding", "gzip"));
				}
				// if we have a content-length at this point, replace it with chunked
				if (contentLength != null && contentLength > 0) {
					part.removeHeader("Content-Length");
					part.setHeader(new MimeHeader("Transfer-Encoding", "Chunked"));
				}
				// always accept gzip in this case
				part.setHeader(new MimeHeader("Accept-Encoding", "gzip"));
			}
			String host = uri == null || uri.getHost() == null ? artifact.getConfig().getHost() : uri.getAuthority();
			if (host == null && endpoint != null) {
				host = endpoint.getConfig().getHost();
			}
			part.setHeader(new MimeHeader("Host", host));
			
			final String username = input == null || input.get("authentication/username") == null ? (artifact.getConfiguration().getUsername() == null && endpoint != null ? endpoint.getConfig().getUsername() : artifact.getConfiguration().getUsername()) : (String) input.get("authentication/username");
			final String password = input == null || input.get("authentication/password") == null ? (artifact.getConfiguration().getPassword() == null && endpoint != null ? endpoint.getConfig().getPassword() : artifact.getConfiguration().getPassword()) : (String) input.get("authentication/password");

			// currently if preemptive is filled in, you can't do ntlm, only basic & bearer both of which don't support domain
			boolean allowNtlm = artifact.getConfiguration().getPreemptiveAuthorizationType() == null && (endpoint == null || endpoint.getConfig().getPreemptiveAuthorizationType() == null);
			
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
			if (endpoint != null && endpoint.getConfig().getBasePath() != null) {
				path += "/" + endpoint.getConfig().getBasePath();
				path = path.replaceAll("[/]{2,}", "/");
			}
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
						String stringified = "";
						if (value != null) {
							if (value instanceof String) {
								stringified = (String) value;
							}
							else if (child.getType() instanceof Marshallable) {
								stringified = ((Marshallable) child.getType()).marshal(value, child.getProperties());
							}
							else {
								stringified = ConverterFactory.getInstance().getConverter().convert(value, String.class);
							}
						}
						path = path.replaceAll("\\{[\\s]*" + child.getName() + "\\b[^}]*\\}", value == null ? "" : stringified);
					}
				}
				if (!path.startsWith("/")) {
					path = "/" + path;
				}
			}
			
			ComplexContent queryContent = input == null ? null : (ComplexContent) input.get("query");
			boolean firstQuery = path.indexOf('?') < 0;
			if (queryContent != null) {
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
								else if (single instanceof ComplexContent) {
									// TODO: in openapi 3 there are rules for serializing complex content
									throw new NotSupportedException("Complex content is not yet supported in the query parameter");
								}
								if (firstValue) {
									if (firstQuery) {
										firstQuery = false;
										path += "?";
									}
									else {
										path += "&";
									}
									// need to start with ";"
									if (CollectionFormat.MATRIX_IMPLODE.equals(collectionFormat)) {
										path += ";";
										// the rest is CSV compatible for arrays
										collectionFormat = CollectionFormat.CSV;
									}
									else if (CollectionFormat.MATRIX_EXPLODE.equals(collectionFormat)) {
										path += ";";
										collectionFormat = CollectionFormat.MULTI;
									}
									// for label formatting, we don't need the actual key
									if (CollectionFormat.LABEL.equals(collectionFormat)) {
										path += ".";
									}
									else {
										path += escapeQuery(name) + "=";
									}
									// if it is multi, we want to keep appending the values as key=value
									firstValue = CollectionFormat.MULTI.equals(collectionFormat);
								}
								else {
									path += (collectionFormat == null ? CollectionFormat.CSV : collectionFormat).getCharacter();
								}
								String stringified = "";
								if (single != null) {
									if (single instanceof String) {
										stringified = (String) single;
									}
									else if (element.getType() instanceof Marshallable) {
										stringified = ((Marshallable) element.getType()).marshal(single, element.getProperties());
									}
									else {
										stringified = ConverterFactory.getInstance().getConverter().convert(single, String.class);
									}
								}
								path += escapeQuery(stringified);
							}
						}
						else if (value instanceof ComplexContent) {
							// TODO: in openapi 3 there are rules for serializing complex content
							throw new NotSupportedException("Complex content is not yet supported in the query parameter");
						}
						else {
							if (firstQuery) {
								firstQuery = false;
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
			// if we have an api query key, inject it
			if (endpoint != null && endpoint.getConfig().getApiQueryKey() != null) {
				String apiQueryName = endpoint.getConfig().getApiQueryName();
				if (apiQueryName == null) {
					apiQueryName = "apiKey";
				}
				if (firstQuery) {
					firstQuery = false;
					path += "?";
				}
				else {
					path += "&";
				}
				path += apiQueryName + "=" + endpoint.getConfig().getApiQueryKey();
			}
			else if (endpoint != null && endpoint.getConfig().getApiQueryName() != null && endpoint.getConfig().getApiQueryKey() == null) {
				String apiQueryKey = input == null ? null : (String) input.get("apiQueryKey");
				if (firstQuery) {
					firstQuery = false;
					path += "?";
				}
				else {
					path += "&";
				}
				path += endpoint.getConfig().getApiQueryName() + "=" + apiQueryKey;
			}
			
			if (endpoint != null && endpoint.getConfig().getUserAgent() != null) {
				part.setHeader(new MimeHeader("User-Agent", endpoint.getConfig().getUserAgent()));
			}
			// if we have an api header key, inject it
			if (endpoint != null && endpoint.getConfig().getApiHeaderKey() != null) {
				String apiHeaderName = endpoint.getConfig().getApiHeaderName();
				if (apiHeaderName == null) {
					apiHeaderName = "apiKey";
				}
				part.setHeader(new MimeHeader(apiHeaderName, endpoint.getConfig().getApiHeaderKey()));
			}
			// if we have a header configured in the endpoint but no fixed value, you have to pass it at runtime
			else if (endpoint != null && endpoint.getConfig().getApiHeaderName() != null && endpoint.getConfig().getApiHeaderKey() == null) {
				String apiHeaderKey = input == null ? null : (String) input.get("apiHeaderKey");
				if (apiHeaderKey != null) {
					part.setHeader(new MimeHeader(endpoint.getConfig().getApiHeaderName(), apiHeaderKey));	
				}
			}
			
			HTTPRequest request = new DefaultHTTPRequest(
				artifact.getConfiguration().getMethod() == null ? "GET" : artifact.getConfiguration().getMethod().toString(),
				path,
				part);
			
			if (artifact.getConfig().getEndpoint() != null && artifact.getConfig().getEndpoint().getConfig().getSecurityType() != null) {
				if (!HTTPRequestAuthenticatorFactory.getInstance().getAuthenticator(artifact.getConfig().getEndpoint().getConfig().getSecurityType())
						.authenticate(request, artifact.getConfig().getEndpoint().getConfig().getSecurityContext(), null, false)) {
					throw new IllegalStateException("Could not authenticate the request");
				}
			}
			
			WebAuthorizationType preemptiveAuthorizationType = artifact.getConfiguration().getPreemptiveAuthorizationType();
			if (preemptiveAuthorizationType == null && endpoint != null) {
				preemptiveAuthorizationType = endpoint.getConfig().getPreemptiveAuthorizationType();
			}
			
			if (preemptiveAuthorizationType != null && principal != null) {
				switch(preemptiveAuthorizationType) {
					case BASIC:
						request.getContent().setHeader(new MimeHeader(HTTPUtils.SERVER_AUTHENTICATE_RESPONSE, new BasicAuthentication().authenticate(principal, "basic")));
					break;
					case BEARER:
						request.getContent().setHeader(new MimeHeader(HTTPUtils.SERVER_AUTHENTICATE_RESPONSE, "Bearer " + principal.getName()));
					break;
				}
			}
			
			boolean isSecure = false;
			if (uri != null && uri.getScheme() != null) {
				isSecure = "https".equalsIgnoreCase(uri.getScheme());
			}
			else if (artifact.getConfig().getSecure() != null) {
				isSecure = artifact.getConfig().getSecure();
			}
			else if (endpoint != null && endpoint.getConfig().getSecure() != null) {
				isSecure = endpoint.getConfig().getSecure();
			}
			HTTPClient client = Services.getTransactionable(executionContext, input == null ? null : (String) input.get("transactionId"), artifact.getConfiguration().getHttpClient() == null && endpoint != null ? endpoint.getConfig().getHttpClient() : artifact.getConfiguration().getHttpClient()).getClient();
			
			if (endpoint != null && endpoint.getConfig().isOmitContentLengthIfEmpty()) {
				if (request.getMethod().equalsIgnoreCase("GET")) {
					Header contentLengthHeader = MimeUtils.getHeader("Content-Length", request.getContent().getHeaders());
					if (contentLengthHeader != null && "0".equals(contentLengthHeader.getValue())) {
						request.getContent().removeHeader("Content-Length");
					}
				}
			}
			
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
				throw new ServiceException("REST-CLIENT-" + response.getCode(), "An error occurred on the remote server: [" + response.getCode() + "] " + response.getMessage() + (content == null ? "" : "\n" + new String(content)));
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
						else if ("application/json".equalsIgnoreCase(responseContentType) || "application/javascript".equalsIgnoreCase(responseContentType) || "application/x-javascript".equalsIgnoreCase(responseContentType)
								|| "application/problem+json".equalsIgnoreCase(responseContentType) || (responseContentType != null && responseContentType.matches("application/[\\w]+\\+json"))) {
							JSONBinding jsonBinding = new JSONBinding((ComplexType) artifact.getConfiguration().getOutput(), charset);
							jsonBinding.setIgnoreRootIfArrayWrapper(artifact.getConfig().isIgnoreRootIfArrayWrapper());
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
						if (response.getCode() != 204) {
							ReadableContainer<ByteBuffer> readable = ((ContentPart) response.getContent()).getReadable();
							if (readable != null) {
								ComplexContent unmarshal = binding.unmarshal(IOUtils.toInputStream(readable), new Window[0]);
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
		catch (ServiceException e) {
			throw e;
		}
		catch (Exception e) {
			throw new ServiceException(e);
		}
	}

}
