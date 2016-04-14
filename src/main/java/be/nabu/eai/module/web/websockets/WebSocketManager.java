package be.nabu.eai.module.web.websockets;

import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.managers.base.JAXBArtifactManager;
import be.nabu.libs.resources.api.ResourceContainer;

public class WebSocketManager extends JAXBArtifactManager<WebSocketConfiguration, WebSocketProvider> {

	public WebSocketManager() {
		super(WebSocketProvider.class);
	}

	@Override
	protected WebSocketProvider newInstance(String id, ResourceContainer<?> container, Repository repository) {
		return new WebSocketProvider(id, container, repository);
	}

}
