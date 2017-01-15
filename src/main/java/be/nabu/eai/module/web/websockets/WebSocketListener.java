package be.nabu.eai.module.web.websockets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import be.nabu.eai.module.web.application.WebApplication;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.events.api.EventHandler;
import be.nabu.libs.http.glue.GlueListener.PathAnalysis;
import be.nabu.libs.http.server.websockets.WebSocketUtils;
import be.nabu.libs.http.server.websockets.api.OpCode;
import be.nabu.libs.http.server.websockets.api.WebSocketMessage;
import be.nabu.libs.http.server.websockets.api.WebSocketRequest;
import be.nabu.libs.nio.api.StandardizedMessagePipeline;
import be.nabu.libs.services.api.ServiceResult;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.KeyValuePair;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.binding.api.Window;
import be.nabu.libs.types.binding.json.JSONBinding;
import be.nabu.libs.types.utils.KeyValuePairImpl;
import be.nabu.utils.io.IOUtils;

public class WebSocketListener implements EventHandler<WebSocketRequest, WebSocketMessage> {

	private WebApplication application;
	private WebSocketProvider provider;
	private PathAnalysis analysis;
	private Object configuration;

	public WebSocketListener(WebApplication application, PathAnalysis analysis, WebSocketProvider provider, Object configuration) {
		this.application = application;
		this.analysis = analysis;
		this.provider = provider;
		this.configuration = configuration;
	}

	@Override
	public WebSocketMessage handle(WebSocketRequest event) {
		// we try to parse the incoming request
		try {
			ComplexType input = provider.getConfiguration().getMessageService().getServiceInterface().getInputDefinition();
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
				else if (child.getType() instanceof SimpleType && ((SimpleType<?>) child.getType()).getInstanceClass().equals(byte[].class)) {
					content = input.newInstance();
					content.set(child.getName(), IOUtils.toBytes(IOUtils.wrap(event.getData())));
					break;
				}
				else if (child.getType() instanceof SimpleType && ((SimpleType<?>) child.getType()).getInstanceClass().equals(InputStream.class)) {
					content = input.newInstance();
					content.set(child.getName(), event.getData());
					break;
				}
			}
			if (content == null) {
				throw new RuntimeException("Could not unmarshal the incoming data");
			}
			// fill in the default fields
			else {
				StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage> pipeline = WebSocketUtils.getPipeline();
				if (pipeline != null) {
					SocketAddress remoteSocketAddress = WebSocketUtils.getPipeline().getSourceContext().getSocketAddress();
					content.set("host", remoteSocketAddress instanceof InetSocketAddress ? ((InetSocketAddress) remoteSocketAddress).getHostString() : null);
					content.set("port", remoteSocketAddress instanceof InetSocketAddress ? ((InetSocketAddress) remoteSocketAddress).getPort() : 0);
					content.set("token", WebSocketUtils.getToken(pipeline));
					content.set("webApplicationId", application.getId());
					content.set("path", event.getPath());
					content.set("configuration", configuration);
					if (!analysis.getParameters().isEmpty()) {
						Map<String, String> analyze = analysis.analyze(event.getPath());
						List<KeyValuePair> values = new ArrayList<KeyValuePair>();
						for (String key : analyze.keySet()) {
							values.add(new KeyValuePairImpl(key, analyze.get(key)));
						}
						content.set("pathValues", values);
					}
				}
			}
			Token token = WebSocketUtils.getToken(WebSocketUtils.getPipeline());
			Future<ServiceResult> run = provider.getRepository().getServiceRunner().run(
				provider.getConfiguration().getMessageService(), 
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

	public WebSocketProvider getProvider() {
		return provider;
	}

}
