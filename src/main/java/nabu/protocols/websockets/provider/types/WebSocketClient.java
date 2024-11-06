/*
* Copyright (C) 2016 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

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
	private String pipelineId;
	private String path;
	
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

	public String getPipelineId() {
		return pipelineId;
	}

	public void setPipelineId(String pipelineId) {
		this.pipelineId = pipelineId;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

}
