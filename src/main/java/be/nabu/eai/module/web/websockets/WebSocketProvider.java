package be.nabu.eai.module.web.websockets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.nabu.eai.module.web.application.WebApplication;
import be.nabu.eai.module.web.application.WebFragment;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.libs.authentication.api.Permission;
import be.nabu.libs.events.api.EventSubscription;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.glue.GlueTokenResolver;
import be.nabu.libs.http.server.HTTPServerUtils;
import be.nabu.libs.http.server.nio.MemoryMessageDataProvider;
import be.nabu.libs.http.server.websockets.WebSocketHandshakeHandler;
import be.nabu.libs.http.server.websockets.WebSocketUtils;
import be.nabu.libs.http.server.websockets.api.WebSocketMessage;
import be.nabu.libs.http.server.websockets.api.WebSocketRequest;
import be.nabu.libs.resources.api.ResourceContainer;

// TODO: add ability to validate role/permission of user before connection
public class WebSocketProvider extends JAXBArtifact<WebSocketConfiguration> implements WebFragment {

	private Map<String, List<EventSubscription<?, ?>>> subscriptions = new HashMap<String, List<EventSubscription<?, ?>>>();
	
	public WebSocketProvider(String id, ResourceContainer<?> directory, Repository repository) {
		super(id, directory, repository, "websocket-provider.xml", WebSocketConfiguration.class);
	}

	private String getKey(WebApplication artifact, String path) {
		return artifact.getId() + ":" + path;
	}
	
	@Override
	public void start(WebApplication application, String path) throws IOException {
		String key = getKey(application, path);
		if (subscriptions.containsKey(key)) {
			stop(application, path);
		}
		String artifactPath = application.getConfiguration().getPath() == null || application.getConfiguration().getPath().isEmpty() ? "/" : application.getConfiguration().getPath();
		if (artifactPath.endsWith("/")) {
			artifactPath = artifactPath.substring(0, artifactPath.length() - 1);
		}
		if (getConfiguration().getServerPath() != null && !getConfiguration().getServerPath().isEmpty()) {
			artifactPath += (getConfiguration().getServerPath().startsWith("/") ? "" : "/") + getConfiguration().getServerPath();
		}
		if (application.getConfiguration().getVirtualHost() != null) {
			subscriptions.put(key, new ArrayList<EventSubscription<?, ?>>());
			// create the websocket handshaker
			WebSocketHandshakeHandler websocketHandshakeHandler = new WebSocketHandshakeHandler(application.getConfiguration().getVirtualHost().getDispatcher(), new MemoryMessageDataProvider(1024*1024*10), false);
			websocketHandshakeHandler.setRequireUpgrade(true);
			websocketHandshakeHandler.setTokenResolver(new GlueTokenResolver(application.getSessionProvider(), application.getRealm()));
			// register it
			EventSubscription<HTTPRequest, HTTPResponse> subscription = application.getConfiguration().getVirtualHost().getDispatcher().subscribe(HTTPRequest.class, websocketHandshakeHandler);
			subscription.filter(HTTPServerUtils.limitToPath(artifactPath));
			subscriptions.get(key).add(subscription);
			// create the websocket listener
			EventSubscription<WebSocketRequest, WebSocketMessage> websocketSubscription = application.getConfiguration().getVirtualHost().getDispatcher().subscribe(WebSocketRequest.class, new WebSocketListener(application, artifactPath, this));
			websocketSubscription.filter(WebSocketUtils.limitToPath(artifactPath));
			subscriptions.get(key).add(websocketSubscription);
		}
	}

	@Override
	public void stop(WebApplication application, String path) {
		String key = getKey(application, path);
		if (subscriptions.containsKey(key)) {
			synchronized(subscriptions) {
				if (subscriptions.containsKey(key)) {
					for (EventSubscription<?, ?> subscription : subscriptions.get(key)) {
						subscription.unsubscribe();
					}
					subscriptions.remove(key);
				}
			}
		}	
	}

	@Override
	public List<Permission> getPermissions(WebApplication artifact, String path) {
		return null;
	}

	@Override
	public boolean isStarted(WebApplication application, String path) {
		return subscriptions.containsKey(getKey(application, path));
	}

}
