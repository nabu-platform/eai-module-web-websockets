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
import be.nabu.libs.authentication.api.Device;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.http.api.server.HTTPServer;
import be.nabu.libs.http.server.websockets.WebSocketUtils;
import be.nabu.libs.http.server.websockets.api.OpCode;
import be.nabu.libs.http.server.websockets.api.WebSocketMessage;
import be.nabu.libs.http.server.websockets.api.WebSocketRequest;
import be.nabu.libs.http.server.websockets.impl.WebSocketRequestParserFactory;
import be.nabu.libs.nio.PipelineUtils;
import be.nabu.libs.nio.api.MessageParserFactory;
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
	
	private boolean matches(WebApplication artifact, StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage> pipeline, List<String> users, List<String> roles, List<String> devices, List<WebSocketClient> clients, List<String> notUsers, List<String> notRoles, List<String> notDevices, List<String> pipelineIds) throws IOException {
		// we want to target specific pipelines
		if (pipelineIds != null && !pipelineIds.isEmpty()) {
			if (!pipelineIds.contains(PipelineUtils.getPipelineId(pipeline))) {
				return false;
			}
		}
		// we want to target users/roles
		if ((users != null && !users.isEmpty()) || (roles != null && !roles.isEmpty()) || (clients != null && !clients.isEmpty()) || (notUsers != null && !notUsers.isEmpty()) || (notRoles != null && !notRoles.isEmpty()) || (notDevices != null && !notDevices.isEmpty())) {
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
			if (notRoles != null && !notRoles.isEmpty()) {
				if (artifact.getRoleHandler() == null) {
					throw new IllegalStateException("Role filtering requested but the web application '" + artifact.getId() + "' does not have a role handler");
				}
				for (String role : notRoles) {
					if (artifact.getRoleHandler().hasRole(token, role)) {
						return false;
					}
				}
			}
			if (users != null && !users.isEmpty()) {
				if (token == null || !users.contains(token.getName())) {
					return false;
				}
			}
			if (notUsers != null && !notUsers.isEmpty()) {
				if (token != null && notUsers.contains(token.getName())) {
					return false;
				}
			}
			Device device = WebSocketUtils.getDevice(pipeline);
			if (devices != null && !devices.isEmpty()) {
				if (device == null || !devices.contains(device.getDeviceId())) {
					return false;
				}
			}
			if (notDevices != null && !notDevices.isEmpty()) {
				if (device != null && notDevices.contains(device.getDeviceId())) {
					return false;
				}
			}
			if (clients != null && !clients.isEmpty()) {
				boolean matched = false;
				for (WebSocketClient client : clients) {
					if (client.getToken() != null && !client.getToken().equals(token)) {
						continue;
					}
					SocketAddress remoteSocketAddress = pipeline.getSourceContext().getSocketAddress();
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
	@WebResult(name = "clients")
	public List<WebSocketClient> broadcast(@WebParam(name = "webApplicationId") @NotNull String webApplicationId, @WebParam(name = "path") String path, @WebParam(name = "object") @NotNull Object content, @WebParam(name = "users") List<String> users, @WebParam(name = "roles") List<String> roles, @WebParam(name = "devices") List<String> devices, @WebParam(name = "clients") List<WebSocketClient> clients, @WebParam(name = "webSocketInstanceIds") List<String> webSocketInstanceIds, @WebParam(name = "notUsers") List<String> notUsers, @WebParam(name = "notRoles") List<String> notRoles, @WebParam(name = "notDevices") List<String> notDevices) throws IOException {
		WebApplication artifact = executionContext.getServiceContext().getResolver(WebApplication.class).resolve(webApplicationId);
		HTTPServer server = artifact.getConfiguration().getVirtualHost().getServer().getServer();
		byte [] bytes = null;
		// we want to return a list of clients that we delivered the message to
		List<WebSocketClient> resultingClients = new ArrayList<WebSocketClient>();
		for (StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage> pipeline : WebSocketUtils.getWebsocketPipelines((NIOServer) server, path)) {
			if (!matches(artifact, pipeline, users, roles, devices, clients, notUsers, notRoles, notDevices, webSocketInstanceIds)) {
				continue;
			}
			if (bytes == null) {
				if (content instanceof byte[]) {
					bytes = (byte[]) content;
				}
				else if (content instanceof InputStream) {
					bytes = IOUtils.toBytes(IOUtils.wrap((InputStream) content));
				}
				else if (content instanceof String) {
					bytes = content.toString().getBytes("UTF-8");
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
			
			WebSocketClient client = new WebSocketClient();
			client.setToken(WebSocketUtils.getToken(pipeline));
			client.setDevice(WebSocketUtils.getDevice(pipeline));
			SocketAddress remoteSocketAddress = pipeline.getSourceContext().getSocketAddress();
			client.setHost(remoteSocketAddress instanceof InetSocketAddress ? ((InetSocketAddress) remoteSocketAddress).getHostString() : null);
			client.setPort(remoteSocketAddress instanceof InetSocketAddress ? ((InetSocketAddress) remoteSocketAddress).getPort() : 0);
			client.setCreated(pipeline.getSourceContext().getCreated());
			resultingClients.add(client);
		}
		return resultingClients;
	}
	
	@WebResult(name = "clients")
	public List<WebSocketClient> clients(@WebParam(name = "webApplicationId") @NotNull String webApplicationId, @WebParam(name = "path") String path) throws IOException {
		WebApplication artifact = executionContext.getServiceContext().getResolver(WebApplication.class).resolve(webApplicationId);
		List<WebSocketClient> clients = new ArrayList<WebSocketClient>();
		HTTPServer server = artifact.getConfiguration().getVirtualHost().getServer().getServer();
		for (StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage> pipeline : WebSocketUtils.getWebsocketPipelines((NIOServer) server, path)) {
			WebSocketClient client = new WebSocketClient();
			client.setToken(WebSocketUtils.getToken(pipeline));
			client.setDevice(WebSocketUtils.getDevice(pipeline));
			SocketAddress remoteSocketAddress = pipeline.getSourceContext().getSocketAddress();
			client.setHost(remoteSocketAddress instanceof InetSocketAddress ? ((InetSocketAddress) remoteSocketAddress).getHostString() : null);
			client.setPort(remoteSocketAddress instanceof InetSocketAddress ? ((InetSocketAddress) remoteSocketAddress).getPort() : 0);
			client.setCreated(pipeline.getSourceContext().getCreated());
			client.setPipelineId(PipelineUtils.getPipelineId(pipeline));
			MessageParserFactory<?> requestParserFactory = ((StandardizedMessagePipeline<?, ?>) pipeline).getRequestParserFactory();
			if (requestParserFactory instanceof WebSocketRequestParserFactory) {
				client.setPath(((WebSocketRequestParserFactory) requestParserFactory).getPath());
			}
			clients.add(client);
		}
		Collections.sort(clients);
		return clients;
	}
	
	public void disconnect(@WebParam(name = "webApplicationId") @NotNull String webApplicationId, @WebParam(name = "path") String path, @WebParam(name = "users") List<String> users, @WebParam(name = "roles") List<String> roles, @WebParam(name = "devices") List<String> devices, @WebParam(name = "clients") List<WebSocketClient> clients, @WebParam(name = "webSocketInstanceIds") List<String> webSocketInstanceIds, @WebParam(name = "notUsers") List<String> notUsers, @WebParam(name = "notRoles") List<String> notRoles, @WebParam(name = "notDevices") List<String> notDevices) throws IOException {
		WebApplication artifact = executionContext.getServiceContext().getResolver(WebApplication.class).resolve(webApplicationId);
		HTTPServer server = artifact.getConfiguration().getVirtualHost().getServer().getServer();
		for (StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage> pipeline : WebSocketUtils.getWebsocketPipelines((NIOServer) server, path)) {
			if (!matches(artifact, pipeline, users, roles, devices, clients, notUsers, notRoles, notDevices, webSocketInstanceIds)) {
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
	
	public void setContext(@WebParam(name = "webApplicationId") @NotNull String webApplicationId, @WebParam(name = "path") String path, @NotNull @WebParam(name = "webSocketInstanceId") String webSocketInstanceId, @NotNull @WebParam(name = "key") String key, @WebParam(name = "value") Object value) {
		WebApplication artifact = executionContext.getServiceContext().getResolver(WebApplication.class).resolve(webApplicationId);
		HTTPServer server = artifact.getConfig().getVirtualHost().getServer().getServer();
		for (StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage> pipeline : WebSocketUtils.getWebsocketPipelines((NIOServer) server, path)) {
			if (webSocketInstanceId.equals(PipelineUtils.getPipelineId(pipeline))) {
				if (value == null) {
					pipeline.getContext().remove(key);
				}
				else {
					pipeline.getContext().put(key, value);
				}
			}
		}
	}
	
	@WebResult(name = "value")
	public Object getContext(@WebParam(name = "webApplicationId") @NotNull String webApplicationId, @WebParam(name = "path") String path, @NotNull @WebParam(name = "webSocketInstanceId") String webSocketInstanceId, @NotNull @WebParam(name = "key") String key) {
		WebApplication artifact = executionContext.getServiceContext().getResolver(WebApplication.class).resolve(webApplicationId);
		HTTPServer server = artifact.getConfig().getVirtualHost().getServer().getServer();
		for (StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage> pipeline : WebSocketUtils.getWebsocketPipelines((NIOServer) server, path)) {
			if (webSocketInstanceId.equals(PipelineUtils.getPipelineId(pipeline))) {
				return pipeline.getContext().get(key);
			}
		}
		return null;
	}
	
	public void authenticate(@WebParam(name = "webApplicationId") @NotNull String webApplicationId, @WebParam(name = "path") String path, @NotNull @WebParam(name = "webSocketInstanceId") String webSocketInstanceId, @WebParam(name = "token") Token token) {
		WebApplication artifact = executionContext.getServiceContext().getResolver(WebApplication.class).resolve(webApplicationId);
		HTTPServer server = artifact.getConfig().getVirtualHost().getServer().getServer();
		for (StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage> pipeline : WebSocketUtils.getWebsocketPipelines((NIOServer) server, path)) {
			if (webSocketInstanceId.equals(PipelineUtils.getPipelineId(pipeline))) {
				((WebSocketRequestParserFactory) pipeline.getRequestParserFactory()).setToken(token);
			}
		}
	}
	
	public void ping(@WebParam(name = "webApplicationId") @NotNull String webApplicationId, @WebParam(name = "path") String path, @NotNull @WebParam(name = "webSocketInstanceId") String webSocketInstanceId) {
		WebApplication artifact = executionContext.getServiceContext().getResolver(WebApplication.class).resolve(webApplicationId);
		HTTPServer server = artifact.getConfig().getVirtualHost().getServer().getServer();
		for (StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage> pipeline : WebSocketUtils.getWebsocketPipelines((NIOServer) server, path)) {
			if (webSocketInstanceId.equals(PipelineUtils.getPipelineId(pipeline))) {
				WebSocketUtils.pingPong(pipeline);
			}
		}
	}
}
