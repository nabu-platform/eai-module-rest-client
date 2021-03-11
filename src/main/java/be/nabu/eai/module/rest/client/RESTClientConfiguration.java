package be.nabu.eai.module.rest.client;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.api.Advanced;
import be.nabu.eai.api.EnvironmentSpecific;
import be.nabu.eai.api.LargeText;
import be.nabu.eai.module.http.client.HTTPClientArtifact;
import be.nabu.eai.module.rest.RESTConfiguration;
import be.nabu.eai.module.rest.WebResponseType;
import be.nabu.eai.repository.jaxb.ArtifactXMLAdapter;
import be.nabu.libs.http.api.WebAuthorizationType;

@XmlRootElement(name = "restClient")
@XmlType(propOrder = { "host", "secure", "httpClient", "preemptiveAuthorizationType", "username", "password", "requestType", "responseType", "charset", "gzip", "sanitizeOutput", "validateInput", "validateOutput", "lenient", "description", "ignoreRootIfArrayWrapper", "endpoint" })
public class RESTClientConfiguration extends RESTConfiguration {
	
	private HTTPClientArtifact httpClient;
	private String username, password;
	private WebResponseType requestType, responseType;
	private String charset;
	private Boolean gzip;
	private String host, description;
	private Boolean secure, sanitizeOutput;
	private WebAuthorizationType preemptiveAuthorizationType;
	private Boolean validateInput, validateOutput;
	// for backwards compatibility it has to be true
	private boolean ignoreRootIfArrayWrapper = true;
	private boolean lenient = true;
	
	private RESTEndpointArtifact endpoint;
	
	@Advanced
	@EnvironmentSpecific
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public HTTPClientArtifact getHttpClient() {
		return httpClient;
	}
	public void setHttpClient(HTTPClientArtifact httpClient) {
		this.httpClient = httpClient;
	}
	
	@EnvironmentSpecific
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	
	@EnvironmentSpecific
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
	
	@Advanced
	public String getCharset() {
		return charset;
	}
	public void setCharset(String charset) {
		this.charset = charset;
	}
	
	@Advanced
	@EnvironmentSpecific
	public Boolean getGzip() {
		return gzip;
	}
	public void setGzip(Boolean gzip) {
		this.gzip = gzip;
	}
	
	@EnvironmentSpecific
	public String getHost() {
		return host;
	}
	public void setHost(String host) {
		this.host = host;
	}
	
	@EnvironmentSpecific
	public Boolean getSecure() {
		return secure;
	}
	public void setSecure(Boolean secure) {
		this.secure = secure;
	}
	
	@Advanced
	@EnvironmentSpecific
	public WebAuthorizationType getPreemptiveAuthorizationType() {
		return preemptiveAuthorizationType;
	}
	public void setPreemptiveAuthorizationType(WebAuthorizationType preemptiveAuthorizationType) {
		this.preemptiveAuthorizationType = preemptiveAuthorizationType;
	}
	
	@Advanced
	public Boolean getSanitizeOutput() {
		return sanitizeOutput;
	}
	public void setSanitizeOutput(Boolean sanitizeOutput) {
		this.sanitizeOutput = sanitizeOutput;
	}
	
	public Boolean getValidateInput() {
		return validateInput;
	}
	public void setValidateInput(Boolean validateInput) {
		this.validateInput = validateInput;
	}
	public Boolean getValidateOutput() {
		return validateOutput;
	}
	public void setValidateOutput(Boolean validateOutput) {
		this.validateOutput = validateOutput;
	}
	public WebResponseType getResponseType() {
		return responseType;
	}
	public void setResponseType(WebResponseType responseType) {
		this.responseType = responseType;
	}
	
	@Advanced
	public boolean isLenient() {
		return lenient;
	}
	public void setLenient(boolean lenient) {
		this.lenient = lenient;
	}
	
	@LargeText
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	
	@Advanced
	public boolean isIgnoreRootIfArrayWrapper() {
		return ignoreRootIfArrayWrapper;
	}
	public void setIgnoreRootIfArrayWrapper(boolean ignoreRootIfArrayWrapper) {
		this.ignoreRootIfArrayWrapper = ignoreRootIfArrayWrapper;
	}
	
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public RESTEndpointArtifact getEndpoint() {
		return endpoint;
	}
	public void setEndpoint(RESTEndpointArtifact endpoint) {
		this.endpoint = endpoint;
	}

}
