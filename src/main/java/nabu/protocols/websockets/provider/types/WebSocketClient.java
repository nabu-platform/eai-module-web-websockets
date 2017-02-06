package nabu.protocols.websockets.provider.types;

import java.util.Date;

import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlRootElement;

import be.nabu.libs.authentication.api.Device;
import be.nabu.libs.authentication.api.Token;

@XmlRootElement
public class WebSocketClient implements Comparable<WebSocketClient> {
	private String host;
	private Integer port;
	private Token token;
	private Date created;
	private Device device;
	
	public WebSocketClient() {
		// auto construct
	}
	
	public WebSocketClient(String host, Integer port, Token token, Device device, Date created) {
		this.host = host;
		this.port = port;
		this.token = token;
		this.device = device;
		this.created = created;
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

	@NotNull
	public Date getCreated() {
		return created;
	}
	public void setCreated(Date created) {
		this.created = created;
	}

	@Override
	public int compareTo(WebSocketClient o) {
		if (created == null && o.created == null) {
			return 0;
		}
		else if (created == null) {
			return -1;
		}
		else if (o.created == null) {
			return 1;
		}
		else {
			return created.compareTo(o.created);
		}
	}

	public Device getDevice() {
		return device;
	}

	public void setDevice(Device device) {
		this.device = device;
	}
	

}
