package be.nabu.module.rest.client;

import java.net.URI;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.repository.artifacts.web.rest.WebResponseType;
import be.nabu.eai.repository.jaxb.ArtifactXMLAdapter;
import be.nabu.module.protocol.http.artifact.HTTPClientArtifact;
import be.nabu.module.rest.RESTConfiguration;

@XmlRootElement(name = "restClient")
@XmlType(propOrder = { "url", "httpClient", "username", "password", "requestType", "charset", "gzip" })
public class RESTClientConfiguration extends RESTConfiguration {
	
	private URI url;
	private HTTPClientArtifact httpClient;
	private String username, password;
	private WebResponseType requestType;
	private String charset;
	private Boolean gzip;
	
	public URI getUrl() {
		return url;
	}
	public void setUrl(URI url) {
		this.url = url;
	}
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public HTTPClientArtifact getHttpClient() {
		return httpClient;
	}
	public void setHttpClient(HTTPClientArtifact httpClient) {
		this.httpClient = httpClient;
	}
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	public WebResponseType getRequestType() {
		return requestType;
	}
	public void setRequestType(WebResponseType requestType) {
		this.requestType = requestType;
	}
	public String getCharset() {
		return charset;
	}
	public void setCharset(String charset) {
		this.charset = charset;
	}
	public Boolean getGzip() {
		return gzip;
	}
	public void setGzip(Boolean gzip) {
		this.gzip = gzip;
	}
}
