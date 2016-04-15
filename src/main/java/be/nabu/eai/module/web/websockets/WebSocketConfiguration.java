package be.nabu.eai.module.web.websockets;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.api.InterfaceFilter;
import be.nabu.eai.repository.jaxb.ArtifactXMLAdapter;
import be.nabu.libs.services.api.DefinedService;

@XmlRootElement(name = "websocketProvider")
@XmlType(propOrder = {"serverPath", "messageService", "connectService", "disconnectService"})
public class WebSocketConfiguration {
	private String serverPath;
	private DefinedService messageService, connectService, disconnectService;
	
	public String getServerPath() {
		return serverPath;
	}
	public void setServerPath(String serverPath) {
		this.serverPath = serverPath;
	}
	
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public DefinedService getMessageService() {
		return messageService;
	}
	public void setMessageService(DefinedService messageService) {
		this.messageService = messageService;
	}
	
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	@InterfaceFilter(implement = "be.nabu.eai.module.web.websockets.api.WebSocketConnectionListener.connected")
	public DefinedService getConnectService() {
		return connectService;
	}
	public void setConnectService(DefinedService connectService) {
		this.connectService = connectService;
	}
	
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	@InterfaceFilter(implement = "be.nabu.eai.module.web.websockets.api.WebSocketConnectionListener.disconnected")
	public DefinedService getDisconnectService() {
		return disconnectService;
	}
	public void setDisconnectService(DefinedService disconnectService) {
		this.disconnectService = disconnectService;
	}
	
	
}
