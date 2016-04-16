package nabu.protocols.websockets.provider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.jws.WebParam;
import javax.jws.WebService;
import javax.validation.constraints.NotNull;

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
	
	@SuppressWarnings("unchecked")
	public void broadcast(@WebParam(name = "webApplicationId") @NotNull String webApplicationId, @WebParam(name = "path") String path, @WebParam(name = "object") @NotNull Object content, @WebParam(name = "users") List<String> users, @WebParam(name = "roles") List<String> roles) throws IOException {
		WebApplication artifact = executionContext.getServiceContext().getResolver(WebApplication.class).resolve(webApplicationId);
		HTTPServer server = artifact.getConfiguration().getVirtualHost().getConfiguration().getServer().getServer();
		byte [] bytes = null;
		// TODO: serialize the incoming object into JSON
		for (StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage> pipeline : WebSocketUtils.getWebsocketPipelines((NIOServer) server, path)) {
			// we want to target users/roles
			if ((users != null && !users.isEmpty()) || (roles != null && !roles.isEmpty())) {
				Token token = WebSocketUtils.getToken(pipeline);
				if (artifact.getTokenValidator() != null && !artifact.getTokenValidator().isValid(token)) {
					token = null;
				}
				if (roles != null && !roles.isEmpty()) {
					if (artifact.getRoleHandler() == null) {
						throw new IllegalStateException("Role filtering requested but the web application '" + webApplicationId + "' does not have a role handler");
					}
					boolean hasRole = false;
					for (String role : roles) {
						hasRole = artifact.getRoleHandler().hasRole(token, role);
						if (hasRole) {
							break;
						}
					}
					if (!hasRole) {
						continue;
					}
				}
				if (users != null && !users.isEmpty()) {
					if (token == null || !users.contains(token.getName())) {
						continue;
					}
				}
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
	
}
