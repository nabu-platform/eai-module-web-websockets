package be.nabu.eai.module.web.websockets;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.api.InterfaceFilter;
import be.nabu.eai.repository.jaxb.ArtifactXMLAdapter;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.types.api.DefinedType;

@XmlRootElement(name = "websocketProvider")
@XmlType(propOrder = {"serverPath", "messageService", "connectService", "disconnectService", "configurationType" })
public class WebSocketConfiguration {
	private String serverPath;
	private DefinedService messageService, connectService, disconnectService;
	private DefinedType configurationType;
	
	public String getServerPath() {
		return serverPath;
	}
	public void setServerPath(String serverPath) {
		this.serverPath = serverPath;
	}
	
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	@InterfaceFilter(implement = "be.nabu.eai.module.web.websockets.api.WebSocketConnectionListener.message")
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
	
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public DefinedType getConfigurationType() {
		return configurationType;
	}
	public void setConfigurationType(DefinedType configurationType) {
		this.configurationType = configurationType;
	}
}
