package nabu.protocols.websockets.provider;

import javax.jws.WebParam;
import javax.jws.WebService;
import javax.validation.constraints.NotNull;

import be.nabu.eai.module.http.server.HTTPServerArtifact;
import be.nabu.libs.http.api.server.HTTPServer;
import be.nabu.libs.http.server.websockets.WebSocketUtils;
import be.nabu.libs.http.server.websockets.api.OpCode;
import be.nabu.libs.http.server.websockets.api.WebSocketMessage;
import be.nabu.libs.http.server.websockets.api.WebSocketRequest;
import be.nabu.libs.nio.api.NIOServer;
import be.nabu.libs.nio.api.StandardizedMessagePipeline;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.utils.io.IOUtils;

@WebService
public class Services {
	
	private ExecutionContext executionContext;
	
	public void broadcast(@WebParam(name = "serverId") @NotNull String serverId, @WebParam(name = "path") String path, @WebParam(name = "object") @NotNull Object content) {
		HTTPServerArtifact artifact = executionContext.getServiceContext().getResolver(HTTPServerArtifact.class).resolve(serverId);
		// TODO: serialize the incoming object into JSON
		for (StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage> pipeline : WebSocketUtils.getWebsocketPipelines((NIOServer) artifact.getServer(), path)) {
			pipeline.getResponseQueue().add(
				WebSocketUtils.newMessage(OpCode.TEXT, true, confirmationBytes.length, IOUtils.wrap(confirmationBytes, true))	
			);
		}
	}
	
	// TODO: add a send method (or combine with broadcast?) where you optionally send some target ids of users in (to be matched against the token.getName())
}
