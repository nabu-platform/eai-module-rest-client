package be.nabu.eai.module.rest.client;

import java.nio.charset.Charset;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.api.EnvironmentSpecific;
import be.nabu.eai.module.http.client.HTTPClientArtifact;
import be.nabu.eai.module.rest.WebResponseType;
import be.nabu.eai.repository.jaxb.ArtifactXMLAdapter;
import be.nabu.libs.http.api.WebAuthorizationType;

@XmlRootElement(name = "restEndpoint")
public class RESTEndpointConfiguration {
	private HTTPClientArtifact httpClient;
	private Charset charset;
	private String username, password, userAgent, apiHeaderKey, apiQueryKey;
	private Boolean gzip = true, secure;
	private String host, basePath;
	private String apiHeaderName, apiQueryName;
	private WebAuthorizationType preemptiveAuthorizationType;
	private WebResponseType requestType, responseType;
	
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public HTTPClientArtifact getHttpClient() {
		return httpClient;
	}
	public void setHttpClient(HTTPClientArtifact httpClient) {
		this.httpClient = httpClient;
	}
	
	public Charset getCharset() {
		return charset;
	}
	public void setCharset(Charset charset) {
		this.charset = charset;
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
	public String getUserAgent() {
		return userAgent;
	}
	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
	}
	@EnvironmentSpecific
	public String getApiHeaderKey() {
		return apiHeaderKey;
	}
	public void setApiHeaderKey(String apiHeaderKey) {
		this.apiHeaderKey = apiHeaderKey;
	}
	@EnvironmentSpecific
	public String getApiQueryKey() {
		return apiQueryKey;
	}
	public void setApiQueryKey(String apiQueryKey) {
		this.apiQueryKey = apiQueryKey;
	}
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
	public String getBasePath() {
		return basePath;
	}
	public void setBasePath(String basePath) {
		this.basePath = basePath;
	}
	public String getApiHeaderName() {
		return apiHeaderName;
	}
	public void setApiHeaderName(String apiHeaderName) {
		this.apiHeaderName = apiHeaderName;
	}
	public String getApiQueryName() {
		return apiQueryName;
	}
	public void setApiQueryName(String apiQueryName) {
		this.apiQueryName = apiQueryName;
	}
	public WebAuthorizationType getPreemptiveAuthorizationType() {
		return preemptiveAuthorizationType;
	}
	public void setPreemptiveAuthorizationType(WebAuthorizationType preemptiveAuthorizationType) {
		this.preemptiveAuthorizationType = preemptiveAuthorizationType;
	}
	public WebResponseType getRequestType() {
		return requestType;
	}
	public void setRequestType(WebResponseType requestType) {
		this.requestType = requestType;
	}
	public WebResponseType getResponseType() {
		return responseType;
	}
	public void setResponseType(WebResponseType responseType) {
		this.responseType = responseType;
	}
	@EnvironmentSpecific
	public Boolean getSecure() {
		return secure;
	}
	public void setSecure(Boolean secure) {
		this.secure = secure;
	}
	
}
