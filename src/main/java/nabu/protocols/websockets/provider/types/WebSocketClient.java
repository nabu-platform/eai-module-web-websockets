package nabu.protocols.websockets.provider.types;

import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlRootElement;

import be.nabu.libs.authentication.api.Token;

@XmlRootElement
public class WebSocketClient {
	private String host;
	private Integer port;
	private Token token;
	
	public WebSocketClient() {
		// auto construct
	}
	
	public WebSocketClient(String host, Integer port, Token token) {
		this.host = host;
		this.port = port;
		this.token = token;
	}

	@NotNull
	public String getHost() {
		return host;
	}
	public void setHost(String host) {
		this.host = host;
	}
	
	@NotNull
	public Integer getPort() {
		return port;
	}
	public void setPort(Integer port) {
		this.port = port;
	}
	
	public Token getToken() {
		return token;
	}
	public void setToken(Token token) {
		this.token = token;
	}
	
}
