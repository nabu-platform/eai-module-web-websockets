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
