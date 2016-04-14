package be.nabu.eai.module.web.websockets;

import be.nabu.libs.services.api.DefinedService;

public class WebSocketConfiguration {
	private String serverPath;
	private DefinedService service;
	
	public String getServerPath() {
		return serverPath;
	}
	public void setServerPath(String serverPath) {
		this.serverPath = serverPath;
	}
	public DefinedService getService() {
		return service;
	}
	public void setService(DefinedService service) {
		this.service = service;
	}
}
