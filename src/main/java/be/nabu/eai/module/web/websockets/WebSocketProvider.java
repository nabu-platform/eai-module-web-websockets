package be.nabu.eai.module.web.websockets;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.module.web.application.WebApplication;
import be.nabu.eai.module.web.application.WebFragment;
import be.nabu.eai.module.web.websockets.api.WebSocketConnectionListener;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.eai.repository.util.SystemPrincipal;
import be.nabu.libs.authentication.api.Permission;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.events.api.EventHandler;
import be.nabu.libs.events.api.EventSubscription;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.glue.GlueListener;
import be.nabu.libs.http.glue.GlueListener.PathAnalysis;
import be.nabu.libs.http.glue.GlueTokenResolver;
import be.nabu.libs.http.server.nio.MemoryMessageDataProvider;
import be.nabu.libs.http.server.websockets.WebSocketHandshakeHandler;
import be.nabu.libs.http.server.websockets.WebSocketUtils;
import be.nabu.libs.http.server.websockets.api.WebSocketMessage;
import be.nabu.libs.http.server.websockets.api.WebSocketRequest;
import be.nabu.libs.http.server.websockets.impl.WebSocketRequestParserFactory;
import be.nabu.libs.nio.api.StandardizedMessagePipeline;
import be.nabu.libs.nio.api.events.ConnectionEvent;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.services.pojo.POJOUtils;
import be.nabu.libs.types.api.KeyValuePair;
import be.nabu.libs.types.utils.KeyValuePairImpl;

// TODO: add ability to validate role/permission of user before connection
// TODO: add hooks for new connections & stopped connections (can update state)
public class WebSocketProvider extends JAXBArtifact<WebSocketConfiguration> implements WebFragment {

	private Logger logger = LoggerFactory.getLogger(getClass());
	private Map<String, List<EventSubscription<?, ?>>> subscriptions = new HashMap<String, List<EventSubscription<?, ?>>>();
	
	public WebSocketProvider(String id, ResourceContainer<?> directory, Repository repository) {
		super(id, directory, repository, "websocket-provider.xml", WebSocketConfiguration.class);
	}

	private String getKey(WebApplication artifact, String path) {
		return artifact.getId() + ":" + path;
	}
	
	@Override
	public void start(final WebApplication application, String path) throws IOException {
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
		final PathAnalysis analysis = GlueListener.analyzePath(artifactPath);
		final boolean isRegex = !analysis.getParameters().isEmpty();
		if (application.getConfiguration().getVirtualHost() != null) {
			subscriptions.put(key, new ArrayList<EventSubscription<?, ?>>());
			// create the websocket handshaker
			WebSocketHandshakeHandler websocketHandshakeHandler = new WebSocketHandshakeHandler(application.getConfiguration().getVirtualHost().getDispatcher(), new MemoryMessageDataProvider(1024*1024*10), false);
			websocketHandshakeHandler.setRequireUpgrade(true);
			websocketHandshakeHandler.setTokenResolver(new GlueTokenResolver(application.getSessionProvider(), application.getRealm()));
			// register it
			be.nabu.libs.http.server.PathFilter httpFilter = isRegex
				? new be.nabu.libs.http.server.PathFilter(analysis.getRegex(), true, true)
				: new be.nabu.libs.http.server.PathFilter(artifactPath, false, true);
			EventSubscription<HTTPRequest, HTTPResponse> subscription = application.getConfiguration().getVirtualHost().getDispatcher().subscribe(HTTPRequest.class, websocketHandshakeHandler);
			subscription.filter(httpFilter);
			subscriptions.get(key).add(subscription);
			
			// create the websocket listener
			be.nabu.libs.http.server.websockets.util.PathFilter websocketFilter = isRegex
				? new be.nabu.libs.http.server.websockets.util.PathFilter(analysis.getRegex(), true, true)
				: new be.nabu.libs.http.server.websockets.util.PathFilter(artifactPath, false, true);
			EventSubscription<WebSocketRequest, WebSocketMessage> websocketSubscription = application.getConfiguration().getVirtualHost().getDispatcher().subscribe(WebSocketRequest.class, new WebSocketListener(application, analysis, this));
			websocketSubscription.filter(websocketFilter);
			subscriptions.get(key).add(websocketSubscription);
			
			if (getConfiguration().getConnectService() != null || getConfiguration().getDisconnectService() != null) {
				final WebSocketConnectionListener connectionListener = getConfiguration().getConnectService() != null ? POJOUtils.newProxy(
					WebSocketConnectionListener.class,
					getRepository(),
					SystemPrincipal.ROOT,
					getConfiguration().getConnectService(),
					getConfiguration().getDisconnectService()
				) : null;
				// listen to connect/disconnect events
				final String pathToListen = artifactPath;
				EventSubscription<ConnectionEvent, Void> connectionSubscription = application.getConfiguration().getVirtualHost().getConfiguration().getServer().getServer().getDispatcher().subscribe(ConnectionEvent.class, new EventHandler<ConnectionEvent, Void>() {
					@SuppressWarnings("unchecked")
					@Override
					public Void handle(ConnectionEvent event) {
						try {
							WebSocketRequestParserFactory parserFactory = WebSocketUtils.getParserFactory(event.getPipeline());
							if (parserFactory != null) {
								if ((isRegex && parserFactory.getPath().matches(analysis.getRegex())) || (!isRegex && parserFactory.getPath().equals(pathToListen))) {
									Map<String, String> analyze = analysis.analyze(parserFactory.getPath());
									List<KeyValuePair> values = new ArrayList<KeyValuePair>();
									for (String key : analyze.keySet()) {
										values.add(new KeyValuePairImpl(key, analyze.get(key)));
									}
									SocketAddress remoteSocketAddress = event.getPipeline().getSourceContext().getSocket().getRemoteSocketAddress();
									String host = remoteSocketAddress instanceof InetSocketAddress ? ((InetSocketAddress) remoteSocketAddress).getHostString() : null;
									int port = remoteSocketAddress instanceof InetSocketAddress ? ((InetSocketAddress) remoteSocketAddress).getPort() : 0;
									// upgraded means we have an active websocket connection
									if (ConnectionEvent.ConnectionState.UPGRADED.equals(event.getState()) && getConfiguration().getConnectService() != null) {
										Token token = WebSocketUtils.getToken((StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage>) event.getPipeline());
										try {
											connectionListener.connected(application.getId(), parserFactory.getPath(), token, host, port, values);
										}
										catch (Exception e) {
											event.getPipeline().close();
										}
									}
									// someone disconnected
									else if (ConnectionEvent.ConnectionState.CLOSED.equals(event.getState()) && getConfiguration().getDisconnectService() != null) {
										Token token = WebSocketUtils.getToken((StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage>) event.getPipeline());
										connectionListener.disconnected(application.getId(), parserFactory.getPath(), token, host, port, values);
									}
								}
							}
						}
						catch (Exception e) {
							logger.error("Could not process connection event", e);
						}
						return null;
					}
				});
				subscriptions.get(key).add(connectionSubscription);
			}
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
