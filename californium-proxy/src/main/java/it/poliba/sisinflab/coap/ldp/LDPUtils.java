package it.poliba.sisinflab.coap.ldp;

import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHeaderElement;
import org.apache.http.message.BasicHeaderValueFormatter;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;

public class LDPUtils {

	public static String updatePayloadIRI(String baseUri, Response coapResponse) {
		String payload = coapResponse.getPayloadString();
		return payload.replace("coap://", baseUri + "coap://");
	}
	
	public static ResponseCode getLDPResponseCode(ResponseCode coapCode, Request coapRequest) {
		String ldp = getLDPUriQuery(coapRequest);
		
		if (ldp != null && ldp.equalsIgnoreCase("head") && coapCode.compareTo(ResponseCode.CONTENT) == 0) {
			return ResponseCode.VALID;
		} else
			return coapCode;
	}
	
	public static String getLDPUriQuery(Request coapRequest) {
		return getLDPUriQuery(coapRequest.getOptions().getUriQuery());
	}
	
	private static String getLDPUriQuery(List<String> uriQuery) {
		for(String query : uriQuery) {
			String[] q = query.split("=");
			if (q.length == 2 && q[0].equalsIgnoreCase("ldp")) {
				return q[1];
			}
		}
		return null;
	}
	
	public static String getHTTPLinkHeader(String uri, String rel) {
		return "<" + uri + ">; rel=\"" + rel + "\"";
	}
	
	public static Header[] getLDPOptionsHeaders(Response coapResponse) {
		List<Header> headers = new LinkedList<Header>();
		String response = coapResponse.getPayloadString();		
		JSONParser parser = new JSONParser();
		try {
			JSONObject obj= (JSONObject) parser.parse(response);
			JSONArray allow = (JSONArray) obj.get(LDP.HDR_ALLOW); 
			JSONArray accept_post = (JSONArray) obj.get(LDP.HDR_ACCEPT_POST);
			JSONArray accept_patch = (JSONArray) obj.get(LDP.HDR_ACCEPT_PATCH);
			if(allow != null){
				String allow_string = allow.toJSONString();
				allow_string = allow_string.replace("\"", "");
				allow_string = allow_string.replace("[", "");
				allow_string = allow_string.replace("]", "");
				Header allow_header = new BasicHeader(LDP.HDR_ALLOW, allow_string);
				headers.add(allow_header);
			}
			
			if(accept_post != null){
				String accept_post_string = accept_post.toJSONString();
				accept_post_string = accept_post_string.replace("\"", "");
				accept_post_string = accept_post_string.replace("\\", "");
				accept_post_string = accept_post_string.replace("[", "");
				accept_post_string = accept_post_string.replace("]", "");
				Header accept_post_header = new BasicHeader(LDP.HDR_ACCEPT_POST, accept_post_string);
				headers.add(accept_post_header);
			}
			
			if(accept_patch != null){
				String accept_patch_string = accept_patch.toJSONString();
				accept_patch_string = accept_patch_string.replace("\"", "");
				accept_patch_string = accept_patch_string.replace("\\", "");
				accept_patch_string = accept_patch_string.replace("[", "");
				accept_patch_string = accept_patch_string.replace("]", "");
				Header accept_patch_header = new BasicHeader(LDP.HDR_ACCEPT_PATCH, accept_patch_string);
				headers.add(accept_patch_header);
			}			
		} catch (ParseException e) {
			e.printStackTrace();
		}

		return headers.toArray(new Header[0]);
	}

	public static void setLDPHeaders(String httpMethod, HttpResponse httpResponse, Response coapResponse) {		
		if (httpMethod != null && httpMethod.equalsIgnoreCase("options")) {
			Header[] headers = getLDPOptionsHeaders(coapResponse);
			if (headers.length > 0) {
				for (Header h : headers)
					httpResponse.addHeader(h);
			}			
		}	
	}
	
	public static void setLDPDiscoveryHeaders(String payload, HttpResponse httpResponse) {
		if (payload.length() > 0) {
			int start_index = payload.indexOf("rt=\"", 0);
			String substring = payload.substring(start_index+4);
			int end_index = substring.indexOf('"', 0);
			String rt = substring.substring(0, end_index);
			StringTokenizer tokenizer = new StringTokenizer(rt, " ");
			String resources = ""; 
			
			while(tokenizer.hasMoreElements()){
				String resource = "<"+(String)tokenizer.nextElement() + ">; rel=\"type\", ";
				resources = resources + resource;
			}
			
			resources = resources.substring(0, resources.length()-2);
			Header link = new BasicHeader(LDP.HDR_LINK, resources);
			httpResponse.addHeader(link);		
		}
	}

	public static String getLDPRequestURI(String httpMethod, String uri) {
		if (httpMethod.equalsIgnoreCase("options") 
				|| httpMethod.equalsIgnoreCase("head") 
				|| httpMethod.equalsIgnoreCase("patch")) {
			return uri + "?" + LDP.LINK_LDP + "=" + httpMethod;
		} else
			return uri;
	}

	public static void setLDPParameters(Request coapRequest, Header[] headers) {
		for (Header h : headers) {
			if (h.getName().equalsIgnoreCase(LDP.HDR_SLUG)) {
				String title = h.getValue();
				String uri = updateProxyUri(coapRequest.getOptions().getProxyUri(), "title=" + title);
				coapRequest.getOptions().setProxyUri(uri);
			} else if (h.getName().equalsIgnoreCase(LDP.HDR_LINK)) {
				String[] links = h.getValue().split(",");
				for (String link : links) {
					String[] opt = link.split(";");
					if (opt[1].equalsIgnoreCase("rel=\"type\"")) {
						if (opt[0].contains(LDP.CLASS_RESOURCE)) {
							String uri = updateProxyUri(coapRequest.getOptions().getProxyUri(), "rt=" + LDP.LINK_LDP + ":" + LDP.CLASS_LNAME_RESOURCE);
							coapRequest.getOptions().setProxyUri(uri);
						}
					}
						
				}
			}
		}		
	}
	
	private static String updateProxyUri(String uri, String parameter) {
		if (uri.contains("?"))
			return uri + "&" + parameter;
		else
			return uri + "?" + parameter;
	}

	public static byte[] updateLDPPayload(byte[] payload, String host) {
		String rdf = new String(payload, StandardCharsets.UTF_8);
		String uri = "http://" + host + "/proxy/";
		if (rdf.contains(uri))
			rdf = rdf.replace(uri, "");
		return rdf.getBytes();
	}

	public static void setLDPPreferences(Request coapRequest, Header[] headers) {
		for (Header h : headers) {
			if (h.getName().equalsIgnoreCase(LDP.HDR_PREFER)) {
				String prefHeader = h.getValue().split(";")[1].trim();
				String[] pref = prefHeader.split("=");
				
				String type = null;
				if (pref[0].equalsIgnoreCase(LDP.PREFER_OMIT))
					type = LDP.LINK_LDP_PREF_OMIT;
				else if (pref[0].equalsIgnoreCase(LDP.PREFER_INCLUDE)) 
					type = LDP.LINK_LDP_PREF_INCLUDE;
				else 
					continue;
					
				String p = "";
				String[] values = pref[1].replace("\"", "").split(" ");
				for (String value : values) {						
					if (value.equals(LDP.PREFER_CONTAINMENT))
						p = p.concat(LDP.LINK_LDP +":" + LDP.PREFER_LNAME_CONTAINMENT + ";");
					else if (value.equals(LDP.PREFER_MEMBERSHIP))
						p = p.concat(LDP.LINK_LDP +":" + LDP.PREFER_LNAME_MEMBERSHIP + ";");
					else if (value.equals(LDP.PREFER_MINIMAL_CONTAINER))
						p = p.concat(LDP.LINK_LDP +":" + LDP.PREFER_LNAME_MINIMAL_CONTAINER + ";");
					else if (value.equals(LDP.DEPRECATED_PREFER_EMPTY_CONTAINER))
						p = p.concat(LDP.LINK_LDP +":" + "PreferEmptyContainer;");	
				}
				
				if (p.length() > 0) {
					String uri = updateProxyUri(coapRequest.getOptions().getProxyUri(), type + "=" + p.substring(0, p.length()-1));
					coapRequest.getOptions().setProxyUri(uri);
				}
			}
		}	
	}
}
