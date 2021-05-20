package org.toolup.network.http.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.DeserializationConfig.Feature;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.toolup.network.http.HTTPWrapper;
import org.toolup.network.http.HTTPWrapperException;
import org.toolup.network.http.HTTPWrapperException.HTTPVERB;

import com.jayway.jsonpath.JsonPath;

public class HTTPJsonWrapper {

	public static final int LIMIT_MAX_VALUE = 50;
	
	private static final  ObjectMapper objectMapper = new ObjectMapper();
	static {
		objectMapper.configure(Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}
	
	private final Logger logger = LoggerFactory.getLogger(HTTPJsonWrapper.class);
	private final HTTPWrapper httpWrapper = new HTTPWrapper();
	
	private final List<Header> defaultHeaders;
	
	private  String limitParamName = "limit";
	private  String offsetParamName = "offset";
	
	
	public HTTPJsonWrapper() {
		defaultHeaders = new ArrayList<Header>();
	}
	
	
	public HTTPJsonWrapper limitParamName(String limitParamName) {
		this.limitParamName = limitParamName;
		return this;
	}

	public HTTPJsonWrapper offsetParamName(String offsetParamName) {
		this.offsetParamName = offsetParamName;
		return this;
	}
	
	public List<Header> getDefaultHeaders() {
		return defaultHeaders;
	}

	public HTTPJsonWrapper addDefaultHeader(Header... defaultHeaders) {
		if(defaultHeaders != null) this.defaultHeaders.addAll(Arrays.asList(defaultHeaders));
		return this;
	}

	public HTTPJsonWrapper defaultHeaders(Header... defaultHeaders) {
		this.defaultHeaders.clear();
		if(defaultHeaders != null) this.defaultHeaders.addAll(Arrays.asList(defaultHeaders));
		return this;
	}
	
	public HTTPJsonWrapper defaultHeaders(List<Header> defaultHeaders) {
		this.defaultHeaders.clear();
		if(defaultHeaders != null) this.defaultHeaders.addAll(defaultHeaders);
		return this;
	}
	
	// GET
	
	public <T> T readSingle(CloseableHttpClient httpClient, HttpReqParam<T> param) throws HTTPWrapperException {
		if(param == null) return null;
		String url = param.getUrl();
		try {
			Object obj = httpGETParsedJsonDocument(url, httpClient);
			if(logger.isDebugEnabled())
				logger.debug("readSingle {}   -> {}", url, objectMapper.writeValueAsString(obj));
			if(obj == null) return null;
			return objectMapper.readValue(objectMapper.writeValueAsString(obj), param.getClazz());
		}catch(IOException ex) {
			throw new HTTPWrapperException(HTTPVERB.GET, url, ex);
		}
	}
	
	public <T> List<T> readList(CloseableHttpClient httpClient, HttpReqParam<T> param) throws HTTPWrapperException{
		if(param == null) return null;
		String url = param.getUrl();
		try {
			Object obj = httpGETParsedJsonDocument(url, httpClient);
			List<T> result = new ArrayList<>();
			if(obj == null) return result;
			List<Object> res = JsonPath.read(obj, "$.items[*]");
			if(logger.isDebugEnabled()) logger.debug("readList {} ", url);
			for (Object o : res) {
				if(logger.isDebugEnabled()) logger.debug("  -> {}", objectMapper.writeValueAsString(o));
				result.add(objectMapper.readValue(objectMapper.writeValueAsString(o), param.getClazz()));
			}
			return result;
		}catch(IOException ex) {
			throw new HTTPWrapperException(HTTPVERB.GET, url, ex);
		}
	}
	
	public <T> List<T> readAll(CloseableHttpClient httpClient, HttpReqParam<T> param) throws HTTPWrapperException{
		if(param == null) return null;
		String urlBase = param.getUrl();
		try {
			List<T> result = new ArrayList<>();
			
			if(urlBase.contains("?")) 
				throw new HTTPWrapperException(HTTPVERB.GET, urlBase, null, "baseUrl param must not contain '?'.");
			int limit = LIMIT_MAX_VALUE;
			for(int i= 0 ; ; ++i) {
				int offset = limit * i;

				List<T> subResLst = readList(httpClient, param.clone()
							.setUrl(HTTPWrapper.fullUrl(urlBase, httpGetListParams(limit, offset, param.getReqParamsArr()))));
				if(subResLst.isEmpty()) break;
				result.addAll(subResLst);
			}
			return result;
		} catch (HTTPWrapperException ex) {
			throw new HTTPWrapperException(HTTPVERB.GET, urlBase, ex);
		}
	}
	
	public Object httpGETParsedJsonDocument(String url, CloseableHttpClient httpClient) throws HTTPWrapperException {
		try {
			return httpWrapper.httpGETParsedJson(url, httpClient, defaultHeaders);
		} catch(HTTPWrapperException e) {
			if(e.getStatusCode() == 404) return null;
			handleSecurityException(e);
		}
		return null;
	}
	
	public String httpGetListParams(int limit, int offset, NameValuePair... params) throws HTTPWrapperException {
		List<NameValuePair> paramLst = new ArrayList<>();
		if(params != null) 
			paramLst.addAll(Arrays.asList(params));

		if(limit > HTTPJsonWrapper.LIMIT_MAX_VALUE) 
			throw new HTTPWrapperException(HTTPVERB.GET, null, null, String.format( "limit cannot excess %d.", HTTPJsonWrapper.LIMIT_MAX_VALUE));

		paramLst.add(new BasicNameValuePair(limitParamName, Integer.toString(limit)));
		paramLst.add(new BasicNameValuePair(offsetParamName, Integer.toString(offset)));

		return httpGetParams(paramLst.toArray(new NameValuePair[paramLst.size()]));
	}

	public String httpGetParams(NameValuePair... params) throws HTTPWrapperException {
		List<NameValuePair> paramLst = new ArrayList<>();
		if(params != null) paramLst.addAll(Arrays.asList(params));

		StringBuilder res = new StringBuilder();
		if(params == null || params.length == 0) return res.toString();
		for (int i = 0; i < params.length; ++i) {
			NameValuePair param = params[i];
			try {
				res.append(String.format("%s=%s", param.getName(), URLEncoder.encode(param.getValue(), "utf-8")));
			} catch (UnsupportedEncodingException e) {
				throw new HTTPWrapperException(HTTPVERB.GET, null, e);
			}
			if(i < params.length - 1) res.append("&");
		}

		return res.toString();
	}
	
	// POST
		public <T> List<T> postList(CloseableHttpClient httpClient, HttpReqParam<T> param) throws HTTPWrapperException{
			return postList(httpClient, param, "$");
		}
		
		public <T> List<T> postList(CloseableHttpClient httpClient, HttpReqParam<T> param, String listJsonPath) throws HTTPWrapperException{
			String url = param.getUrl();
			try {
				String resp = httpPOST(httpClient, param);
				if(logger.isDebugEnabled())
					logger.debug("postList {} -> {}", url, resp);
				List<T> result = new ArrayList<>();
				if(resp == null) return result;
				
				List<Object> res = JsonPath.read(resp, listJsonPath);
				if(logger.isDebugEnabled())
					logger.debug("postList {}", url);
				for (Object o : res) {
					if(logger.isDebugEnabled())
						logger.debug("  -> {} adding result {}", url, objectMapper.writeValueAsString(o));
					
					result.add(objectMapper.readValue(objectMapper.writeValueAsString(o), param.getClazz()));
				}
				return result;
			}catch(IOException ex) {
				throw new HTTPWrapperException(HTTPVERB.POST, url, ex);
			}
		}
		
		public <T> T postSingle(CloseableHttpClient httpClient, HttpReqParam<T> param) throws HTTPWrapperException {
			String url = param.getUrl();
			String objectValue = null;
			try {
				objectValue = httpPOST(httpClient, param);
				if(logger.isDebugEnabled())
					logger.debug("postSingle {} -> {}", url, objectValue);
				if(objectValue == null) return null;
				return objectMapper.readValue(objectValue, param.getClazz());
			}catch(IOException ex) {
				throw new HTTPWrapperException(HTTPVERB.POST, url , ex,  String.format("postSingle : val = %s", objectValue));
			}
		}
		
		public String httpPOST(CloseableHttpClient httpClient, HttpReqParam<?> param) throws HTTPWrapperException {
			if(param == null) return null;
			Object body = param.getBody();
			try {
				return httpWrapper.httpPOSTParsedJson(param.getUrl()
						, body == null ? null : IOUtils.toInputStream(writeValueAsString(body), "utf-8")
						, httpClient
						, getHeaders(param.getHeadersArr())
						, param.getReqParams()
						, param.getHttpClContext()
						, param.getContentType());
			} catch(HTTPWrapperException e) {
				handleSecurityException(e);
			} catch (IOException ex) {
				throw new HTTPWrapperException(HTTPVERB.POST, param.getUrl(), ex);
			}
			return null;
		}
	
	//PATCH
	
	public <T> T putSingle(CloseableHttpClient httpClient, HttpReqParam<T> param) throws HTTPWrapperException {
		
		try {
			String obj = httpPUT(httpClient, param);
			if(logger.isDebugEnabled())
				logger.debug("patchSingle {}   -> {}", param.getUrl(), objectMapper.writeValueAsString(obj));
			if(obj == null) return null;
			return objectMapper.readValue(obj, param.getClazz());
		}catch(IOException ex) {
			throw new HTTPWrapperException(HTTPVERB.PATCH, param.getUrl(), ex);
		}
	}
	
	public <T> String httpPUT(CloseableHttpClient httpClient, HttpReqParam<T> param) throws HTTPWrapperException {
		if(param == null) return null;
		String url = param.getUrl();
		try {
			InputStream body = param.getBody() == null ? null : IOUtils.toInputStream(objectMapper.writeValueAsString(param.getBody()), "utf-8");
			return httpWrapper.httpPUTContent(url, body , httpClient, getHeaders(param.getHeadersArr()), null);
		} catch(HTTPWrapperException e) {
			handleSecurityException(e);
		} catch (IOException ex) {
			throw new HTTPWrapperException(HTTPVERB.PATCH, url, ex);
		}
		return null;
	}
	
	//PATCH
	
	public <T> T patchSingle(CloseableHttpClient httpClient, HttpReqParam<T> param) throws HTTPWrapperException {
		try {
			String obj = httpPATCH(httpClient, param);
			if(logger.isDebugEnabled())
				logger.debug("patchSingle {}   -> {}", param.getUrl(), objectMapper.writeValueAsString(obj));
			if(obj == null) return null;
			return objectMapper.readValue(obj, param.getClazz());
		}catch(IOException ex) {
			throw new HTTPWrapperException(HTTPVERB.PATCH, param.getUrl(), ex);
		}
	}
	
	
	public <T> String httpPATCH(CloseableHttpClient httpClient, HttpReqParam<T> param) throws HTTPWrapperException {
		if(param == null) return null;
		Object body = param.getBody();
		String url = param.getUrl();
		try {
			return httpWrapper.httpPatchContent(url, body == null ? null : IOUtils.toInputStream(objectMapper.writeValueAsString(body), "utf-8") , httpClient, getHeaders(param.getHeadersArr()), null);
		} catch(HTTPWrapperException e) {
			handleSecurityException(e);
		} catch (IOException ex) {
			throw new HTTPWrapperException(HTTPVERB.PATCH, url, ex);
		}
		return null;
	}
	
	//DELETE
	
	public <T> CloseableHttpResponse httpDELETE(CloseableHttpClient httpClient, HttpReqParam<T> param) throws HTTPWrapperException {
		return httpDELETE(param.getUrl(), httpClient, param.getHeadersArr());
	}
		
	
	public CloseableHttpResponse httpDELETE(String url, CloseableHttpClient httpClient, Header...headers) throws HTTPWrapperException {
		try {
			return httpWrapper.httpDelete(url, httpClient, getHeaders(headers));
		} catch(HTTPWrapperException e) {
			handleSecurityException(e);
		}
		return null;
	}
	
	// MISC
	
	private List<Header> getHeaders(Header...headers){
		List<Header> result = new ArrayList<Header>();
		result.addAll(defaultHeaders);
		if(headers != null)
			result.addAll(Arrays.asList(headers));
		return result;
	}
	
	public void handleSecurityException(HTTPWrapperException e) throws HTTPWrapperException {
		if(e.getStatusCode() == 401) {
			throw new HTTPWrapperException(e.getVerb(), e.getUrl(), e, "Unauthorized : make sure your API credentials are valid.");
		}else if(e.getStatusCode() == 403) {
			throw new HTTPWrapperException(e.getVerb(), e.getUrl(), e, "Forbidden : make sure your user has admin priviledges..");
		}else {
			throw e;
		}
	}
	
	private String writeValueAsString(Object body) throws JsonGenerationException, JsonMappingException, IOException {
		return body instanceof String ? (String)body : objectMapper.writeValueAsString(body);
	}
	
	public static String fullUrl(String baseUrl, String params) {
		if(params == null || params.isEmpty()) return baseUrl;
		return String.format("%s?%s", baseUrl, params);
	}
	
	
}
