package be.nabu.eai.module.web.websockets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.concurrent.Future;

import be.nabu.eai.module.web.application.WebApplication;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.events.api.EventHandler;
import be.nabu.libs.http.server.websockets.WebSocketUtils;
import be.nabu.libs.http.server.websockets.api.OpCode;
import be.nabu.libs.http.server.websockets.api.WebSocketMessage;
import be.nabu.libs.http.server.websockets.api.WebSocketRequest;
import be.nabu.libs.services.api.ServiceResult;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.binding.api.Window;
import be.nabu.libs.types.binding.json.JSONBinding;
import be.nabu.utils.io.IOUtils;

public class WebSocketListener implements EventHandler<WebSocketRequest, WebSocketMessage> {

	private WebApplication application;
	private String path;
	private WebSocketProvider provider;

	public WebSocketListener(WebApplication application, String path, WebSocketProvider provider) {
		this.application = application;
		this.path = path;
		this.provider = provider;
	}

	@Override
	public WebSocketMessage handle(WebSocketRequest event) {
		// we try to parse the incoming request
		try {
			ComplexType input = provider.getConfiguration().getService().getServiceInterface().getInputDefinition();
			ComplexContent content = null;
			for (Element<?> child : input) {
				if (child.getType() instanceof ComplexType) {
					JSONBinding binding = new JSONBinding((ComplexType) child.getType());
					try {
						ComplexContent childContent = binding.unmarshal(event.getData(), new Window[0]);
						content = input.newInstance();
						content.set(child.getName(), childContent);
						break;
					}
					catch (IOException e) {
						continue;
					}
					catch (ParseException e) {
						continue;
					}
				}
			}
			if (content == null) {
				throw new RuntimeException("Could not unmarshal the incoming data");
			}
			Token token = WebSocketUtils.getToken(WebSocketUtils.getPipeline());
			Future<ServiceResult> run = provider.getRepository().getServiceRunner().run(
				provider.getConfiguration().getService(), 
				provider.getRepository().newExecutionContext(token), 
				content 
			);
			ServiceResult serviceResult = run.get();
			if (serviceResult.getException() != null) {
				throw serviceResult.getException();
			}
			else if (serviceResult.getOutput() != null) {
				JSONBinding binding = new JSONBinding(serviceResult.getOutput().getType());
				binding.setIgnoreRootIfArrayWrapper(true);
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				binding.marshal(output, serviceResult.getOutput());
				byte [] bytes = output.toByteArray();
				return WebSocketUtils.newMessage(OpCode.TEXT, true, bytes.length, IOUtils.wrap(bytes, true));	
			}
			return null;
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public WebApplication getApplication() {
		return application;
	}

	public String getPath() {
		return path;
	}

	public WebSocketProvider getProvider() {
		return provider;
	}

}
