package be.nabu.eai.module.web.websockets;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.repository.jaxb.ArtifactXMLAdapter;
import be.nabu.libs.services.api.DefinedService;

@XmlRootElement(name = "websocketProvider")
@XmlType(propOrder = {"serverPath", "service"})
public class WebSocketConfiguration {
	private String serverPath;
	private DefinedService service;
	
	public String getServerPath() {
		return serverPath;
	}
	public void setServerPath(String serverPath) {
		this.serverPath = serverPath;
	}
	
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public DefinedService getService() {
		return service;
	}
	public void setService(DefinedService service) {
		this.service = service;
	}
}
