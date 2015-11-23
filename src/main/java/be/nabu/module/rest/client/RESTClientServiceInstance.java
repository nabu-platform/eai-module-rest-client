package be.nabu.module.rest.client;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collection;

import nabu.protocols.Http;
import be.nabu.eai.repository.artifacts.web.rest.WebResponseType;
import be.nabu.eai.repository.artifacts.web.rest.WebRestArtifact;
import be.nabu.libs.authentication.api.principals.BasicPrincipal;
import be.nabu.libs.http.client.DefaultHTTPClient;
import be.nabu.libs.http.core.DefaultHTTPRequest;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.HTTPResponse;
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
import be.nabu.libs.types.binding.json.JSONBinding;
import be.nabu.libs.types.binding.xml.XMLBinding;
import be.nabu.utils.io.IOUtils;
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
				part = new PlainMimeContentPart(null, IOUtils.wrap((InputStream) object));
			}
			else if (object instanceof ComplexContent) {
				MarshallableBinding binding = WebResponseType.XML.equals(artifact.getConfiguration().getRequestType()) ? new XMLBinding(((ComplexContent) object).getType(), charset) : new JSONBinding(((ComplexContent) object).getType(), charset);
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				binding.marshal(output, (ComplexContent) object);
				byte [] content = output.toByteArray();
				part = new PlainMimeContentPart(null, IOUtils.wrap(content, true), 
					new MimeHeader("Content-Length", Integer.valueOf(content.length).toString()),
					new MimeHeader("Content-Type", WebResponseType.XML.equals(artifact.getConfiguration().getRequestType()) ? "application/xml" : "application/json"));
			}
			else if (object == null) {
				part = new PlainMimeEmptyPart(null);
			}
			else {
				throw new ServiceException("REST-CLIENT-2", "Invalid content");
			}
			
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
			
			if (MimeUtils.getHeader("Content-Type", part.getHeaders()) == null) {
				part.setHeader(new MimeHeader("Content-Type", "application/octet-stream"));
			}
			if (MimeUtils.getHeader("Content-Length", part.getHeaders()) == null) {
				part.setHeader(new MimeHeader("Transfer-Encoding", "Chunked"));
			}
			if (artifact.getConfiguration().getGzip() != null && artifact.getConfiguration().getGzip()) {
				part.setHeader(new MimeHeader("Content-Encoding", "gzip"));
				part.setHeader(new MimeHeader("Accept-Encoding", "gzip"));
			}
			part.setHeader(new MimeHeader("Host", artifact.getConfiguration().getHost()));
			
			final String username = artifact.getConfiguration().getUsername();
			final String password = artifact.getConfiguration().getPassword();
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
			
			HTTPRequest request = new DefaultHTTPRequest(
				artifact.getConfiguration().getMethod() == null ? "GET" : artifact.getConfiguration().getMethod().toString(),
				path,
				part);
			
			boolean isSecure = artifact.getConfiguration().getSecure() != null && artifact.getConfiguration().getSecure();
			DefaultHTTPClient client = Http.getTransactionable(executionContext, (String) input.get("transactionId"), artifact.getConfiguration().getHttpClient()).getClient();
			HTTPResponse response = client.execute(request, principal, isSecure, true);
			
			if (response.getCode() < 200 || response.getCode() >= 300) {
				throw new ServiceException("REST-CLIENT-3", "An error occurred on the remote server: [" + response.getCode() + "] " + response.getMessage());
			}
			
			ComplexContent output = artifact.getServiceInterface().getOutputDefinition().newInstance();
			if (response.getContent() != null) {
				String responseContentType = MimeUtils.getContentType(response.getContent().getHeaders());
				
				if (response.getContent() instanceof ContentPart) {
					if (artifact.getConfiguration().getOutputAsStream() != null && artifact.getConfiguration().getOutputAsStream()) {
						output.set("content", IOUtils.toInputStream(((ContentPart) response.getContent()).getReadable()));
					}
					else if (artifact.getConfiguration().getOutput() != null) {
						WebResponseType responseType;
						if ("application/xml".equals(responseContentType)) {
							responseType = WebResponseType.XML;
						}
						else if ("application/json".equals(responseContentType)) {
							responseType = WebResponseType.JSON;
						}
						else {
							responseType = WebResponseType.XML.equals(artifact.getConfiguration().getRequestType()) ? WebResponseType.XML : WebResponseType.JSON;
						}
						UnmarshallableBinding binding = responseType == WebResponseType.XML
							? new XMLBinding((ComplexType) artifact.getConfiguration().getOutput(), charset)
							: new JSONBinding((ComplexType) artifact.getConfiguration().getOutput(), charset);
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
