package be.nabu.eai.module.rest.client;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.api.EnvironmentSpecific;
import be.nabu.eai.module.http.client.HTTPClientArtifact;
import be.nabu.eai.module.rest.RESTConfiguration;
import be.nabu.eai.module.rest.WebAuthorizationType;
import be.nabu.eai.module.rest.WebResponseType;
import be.nabu.eai.repository.jaxb.ArtifactXMLAdapter;

@XmlRootElement(name = "restClient")
@XmlType(propOrder = { "host", "secure", "httpClient", "preemptiveAuthorizationType", "username", "password", "requestType", "responseType", "charset", "gzip", "sanitizeOutput", "validateInput", "validateOutput" })
public class RESTClientConfiguration extends RESTConfiguration {
	
	private HTTPClientArtifact httpClient;
	private String username, password;
	private WebResponseType requestType, responseType;
	private String charset;
	private Boolean gzip;
	private String host;
	private Boolean secure, sanitizeOutput;
	private WebAuthorizationType preemptiveAuthorizationType;
	private Boolean validateInput, validateOutput;
	
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
	
	public String getCharset() {
		return charset;
	}
	public void setCharset(String charset) {
		this.charset = charset;
	}
	
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
	
	@EnvironmentSpecific
	public WebAuthorizationType getPreemptiveAuthorizationType() {
		return preemptiveAuthorizationType;
	}
	public void setPreemptiveAuthorizationType(WebAuthorizationType preemptiveAuthorizationType) {
		this.preemptiveAuthorizationType = preemptiveAuthorizationType;
	}
	
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
	
}
