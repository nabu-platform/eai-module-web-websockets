package be.nabu.eai.module.web.websockets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import be.nabu.eai.developer.api.InterfaceLister;
import be.nabu.eai.developer.util.InterfaceDescriptionImpl;

public class WebSocketInterfaceLister implements InterfaceLister {

	private static Collection<InterfaceDescription> descriptions = null;
	
	@Override
	public Collection<InterfaceDescription> getInterfaces() {
		if (descriptions == null) {
			synchronized(WebSocketInterfaceLister.class) {
				if (descriptions == null) {
					List<InterfaceDescription> descriptions = new ArrayList<InterfaceDescription>();
					descriptions.add(new InterfaceDescriptionImpl("Web Sockets", "Message Listener", "be.nabu.eai.module.web.websockets.api.WebSocketConnectionListener.message"));
					descriptions.add(new InterfaceDescriptionImpl("Web Sockets", "Connect Listener", "be.nabu.eai.module.web.websockets.api.WebSocketConnectionListener.connected"));
					descriptions.add(new InterfaceDescriptionImpl("Web Sockets", "Disconnect Listener", "be.nabu.eai.module.web.websockets.api.WebSocketConnectionListener.disconnected"));
					WebSocketInterfaceLister.descriptions = descriptions;
				}
			}
		}
		return descriptions;
	}

}
