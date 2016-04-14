package be.nabu.eai.module.web.websockets;

import java.io.IOException;
import java.util.List;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseJAXBGUIManager;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;

public class WebSocketGUIManager extends BaseJAXBGUIManager<WebSocketConfiguration, WebSocketProvider> {

	public WebSocketGUIManager() {
		super("Web Socket Provider", WebSocketProvider.class, new WebSocketManager(), WebSocketConfiguration.class);
	}

	@Override
	protected List<Property<?>> getCreateProperties() {
		return null;
	}

	@Override
	protected WebSocketProvider newInstance(MainController controller, RepositoryEntry entry, Value<?>... values) throws IOException {
		return new WebSocketProvider(entry.getId(), entry.getContainer(), entry.getRepository());
	}

	@Override
	public String getCategory() {
		return "Web";
	}
}
