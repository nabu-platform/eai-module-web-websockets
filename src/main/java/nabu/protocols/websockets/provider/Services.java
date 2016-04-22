package nabu.protocols.websockets.provider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.validation.constraints.NotNull;

import nabu.protocols.websockets.provider.types.WebSocketClient;
import be.nabu.eai.module.web.application.WebApplication;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.http.api.server.HTTPServer;
import be.nabu.libs.http.server.websockets.WebSocketUtils;
import be.nabu.libs.http.server.websockets.api.OpCode;
import be.nabu.libs.http.server.websockets.api.WebSocketMessage;
import be.nabu.libs.http.server.websockets.api.WebSocketRequest;
import be.nabu.libs.nio.api.NIOServer;
import be.nabu.libs.nio.api.StandardizedMessagePipeline;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.types.ComplexContentWrapperFactory;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.binding.json.JSONBinding;
import be.nabu.utils.io.IOUtils;

@WebService
public class Services {
	
	private ExecutionContext executionContext;
	
	private boolean matches(WebApplication artifact, StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage> pipeline, List<String> users, List<String> roles, List<WebSocketClient> clients) throws IOException {
		// we want to target users/roles
		if ((users != null && !users.isEmpty()) || (roles != null && !roles.isEmpty()) || (clients != null && !clients.isEmpty())) {
			Token token = WebSocketUtils.getToken(pipeline);
			if (artifact.getTokenValidator() != null && !artifact.getTokenValidator().isValid(token)) {
				token = null;
			}
			if (roles != null && !roles.isEmpty()) {
				if (artifact.getRoleHandler() == null) {
					throw new IllegalStateException("Role filtering requested but the web application '" + artifact.getId() + "' does not have a role handler");
				}
				boolean hasRole = false;
				for (String role : roles) {
					hasRole = artifact.getRoleHandler().hasRole(token, role);
					if (hasRole) {
						break;
					}
				}
				if (!hasRole) {
					return false;
				}
			}
			if (users != null && !users.isEmpty()) {
				if (token == null || !users.contains(token.getName())) {
					return false;
				}
			}
			if (clients != null && !clients.isEmpty()) {
				boolean matched = false;
				for (WebSocketClient client : clients) {
					if (client.getToken() != null && !client.getToken().equals(token)) {
						continue;
					}
					SocketAddress remoteSocketAddress = pipeline.getSourceContext().getSocket().getRemoteSocketAddress();
					String host = remoteSocketAddress instanceof InetSocketAddress ? ((InetSocketAddress) remoteSocketAddress).getHostString() : null;
					int port = remoteSocketAddress instanceof InetSocketAddress ? ((InetSocketAddress) remoteSocketAddress).getPort() : 0;
					if (client.getHost().equals(host) && client.getPort() == port) {
						matched = true;
					}
				}
				if (!matched) {
					return false;
				}
			}
		}
		return true;
	}
	
	@SuppressWarnings("unchecked")
	public void broadcast(@WebParam(name = "webApplicationId") @NotNull String webApplicationId, @WebParam(name = "path") String path, @WebParam(name = "object") @NotNull Object content, @WebParam(name = "users") List<String> users, @WebParam(name = "roles") List<String> roles, @WebParam(name = "clients") List<WebSocketClient> clients) throws IOException {
		WebApplication artifact = executionContext.getServiceContext().getResolver(WebApplication.class).resolve(webApplicationId);
		HTTPServer server = artifact.getConfiguration().getVirtualHost().getConfiguration().getServer().getServer();
		byte [] bytes = null;
		for (StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage> pipeline : WebSocketUtils.getWebsocketPipelines((NIOServer) server, path)) {
			if (!matches(artifact, pipeline, users, roles, clients)) {
				continue;
			}
			if (bytes == null) {
				if (content instanceof byte[]) {
					bytes = (byte[]) content;
				}
				else if (content instanceof InputStream) {
					bytes = IOUtils.toBytes(IOUtils.wrap((InputStream) content));
				}
				else {
					ComplexContent complexContent = content instanceof ComplexContent ? (ComplexContent) content : ComplexContentWrapperFactory.getInstance().getWrapper().wrap(content);
					JSONBinding binding = new JSONBinding(complexContent.getType());
					ByteArrayOutputStream output = new ByteArrayOutputStream();
					binding.marshal(output, complexContent);
					bytes = output.toByteArray();
				}
			}
			pipeline.getResponseQueue().add(
				WebSocketUtils.newMessage(OpCode.TEXT, true, bytes.length, IOUtils.wrap(bytes, true))	
			);
		}
	}
	
	@WebResult(name = "clients")
	public List<WebSocketClient> clients(@WebParam(name = "webApplicationId") @NotNull String webApplicationId, @WebParam(name = "path") String path) throws IOException {
		WebApplication artifact = executionContext.getServiceContext().getResolver(WebApplication.class).resolve(webApplicationId);
		List<WebSocketClient> clients = new ArrayList<WebSocketClient>();
		HTTPServer server = artifact.getConfiguration().getVirtualHost().getConfiguration().getServer().getServer();
		for (StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage> pipeline : WebSocketUtils.getWebsocketPipelines((NIOServer) server, path)) {
			WebSocketClient client = new WebSocketClient();
			client.setToken(WebSocketUtils.getToken(pipeline));
			SocketAddress remoteSocketAddress = pipeline.getSourceContext().getSocket().getRemoteSocketAddress();
			client.setHost(remoteSocketAddress instanceof InetSocketAddress ? ((InetSocketAddress) remoteSocketAddress).getHostString() : null);
			client.setPort(remoteSocketAddress instanceof InetSocketAddress ? ((InetSocketAddress) remoteSocketAddress).getPort() : 0);
			client.setCreated(pipeline.getSourceContext().getCreated());
			clients.add(client);
		}
		Collections.sort(clients);
		return clients;
	}
	
	public void disconnect(@WebParam(name = "webApplicationId") @NotNull String webApplicationId, @WebParam(name = "path") String path, @WebParam(name = "users") List<String> users, @WebParam(name = "roles") List<String> roles, @WebParam(name = "clients") List<WebSocketClient> clients) throws IOException {
		WebApplication artifact = executionContext.getServiceContext().getResolver(WebApplication.class).resolve(webApplicationId);
		HTTPServer server = artifact.getConfiguration().getVirtualHost().getConfiguration().getServer().getServer();
		for (StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage> pipeline : WebSocketUtils.getWebsocketPipelines((NIOServer) server, path)) {
			if (!matches(artifact, pipeline, users, roles, clients)) {
				continue;
			}
			try {
				pipeline.close();
			}
			catch (Exception e) {
				// ignore
			}
		}
	}
}
