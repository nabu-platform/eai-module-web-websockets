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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.module.web.application.WebApplication;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.events.api.EventHandler;
import be.nabu.libs.events.api.EventTarget;
import be.nabu.libs.http.glue.GlueListener.PathAnalysis;
import be.nabu.libs.http.server.websockets.WebSocketUtils;
import be.nabu.libs.http.server.websockets.api.OpCode;
import be.nabu.libs.http.server.websockets.api.WebSocketMessage;
import be.nabu.libs.http.server.websockets.api.WebSocketRequest;
import be.nabu.libs.metrics.api.MetricInstance;
import be.nabu.libs.nio.PipelineUtils;
import be.nabu.libs.nio.api.StandardizedMessagePipeline;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.SecurityContext;
import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceResult;
import be.nabu.libs.services.api.TransactionContext;
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
	private Logger logger = LoggerFactory.getLogger(getClass());
	private boolean debugWebSockets = Boolean.parseBoolean(System.getProperty("websocket.debug", "false"));

	public WebSocketListener(WebApplication application, PathAnalysis analysis, WebSocketProvider provider, Object configuration) {
		this.application = application;
		this.analysis = analysis;
		this.provider = provider;
		this.configuration = configuration;
	}

	/**
	 * @2022-02-04: this explanation is not entirely complete yet, it is unclear why the retries were attempting because websocket messages do not pass through the regular event handler (which does retries) but rather their own. this may need to be checked later
	 * Apparently when closing a websocket connection, it is customary to send a FF00 message with no payload because a TCP close might take a while to propagate (source lost, was on stack overflow)
	 * We don't actually support specifically FF00 but this handler _will_ fail because it has no content (if content == null => throw)
	 * This actually means the browser client "intends" to close the websocket connection, sends an FF00 message (the actual connection is still alive at that point)
	 * The proxy forwards it to the final server, he does not recognize the FF00 nor require a content, it just forwards
	 * The final server wants to parse the message, it will fail because of the throw and the server will close the connection _before_ the client actually closes the TCP connection
	 * So it more or less works as intended though it is a very weird system.
	 * Because of this too, the proxy can get an early exception back because the connection is closed before it expects it to be closed, for a long time, this would trigger the retry attempt in the proxy _after_ the connection close had already been played out.
	 * So basically:
	 * - client sends message FF00, we see this as attempt 0, the same http client that has been in use at that point was used
	 * - this triggers an error on the server, which closes the connection, triggering a close routine on the proxy which in turn closes (and clears out) the http client. From the proxy perspective the pipeline is closed
	 * - because we got an exception, we go into the catch of the proxy and retry attempt saw a non-existing http client, started one up again
	 * The end result is that the nio http client at the proxy level was originally closed after attempt 0 which triggered the close, then at attempt 1 and 2, a new nio client was created that was _never_ destroyed! this led to nio clients building up
	 */
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
				if (debugWebSockets) {
					byte[] bytes = IOUtils.toBytes(IOUtils.wrap(event.getData()));
					throw new RuntimeException("Could not unmarshal the incoming data: " + new String(bytes));
				}
				else {
					throw new RuntimeException("Could not unmarshal the incoming data");
				}
			}
			// fill in the default fields
			else {
				StandardizedMessagePipeline<WebSocketRequest, WebSocketMessage> pipeline = WebSocketUtils.getPipeline();
				if (pipeline != null) {
					SocketAddress remoteSocketAddress = WebSocketUtils.getPipeline().getSourceContext().getSocketAddress();
					content.set("webSocketId", provider.getId());
					content.set("webSocketInstanceId", PipelineUtils.getPipelineId(pipeline));
					content.set("ip", remoteSocketAddress instanceof InetSocketAddress ? ((InetSocketAddress) remoteSocketAddress).getAddress().getHostAddress() : null);
					content.set("host", remoteSocketAddress instanceof InetSocketAddress ? ((InetSocketAddress) remoteSocketAddress).getHostString() : null);
					content.set("port", remoteSocketAddress instanceof InetSocketAddress ? ((InetSocketAddress) remoteSocketAddress).getPort() : 0);
					content.set("token", WebSocketUtils.getToken(pipeline));
					content.set("device", WebSocketUtils.getDevice(pipeline));
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
			ExecutionContext executionContext = provider.getRepository().newExecutionContext(token);
			Future<ServiceResult> run = provider.getRepository().getServiceRunner().run(
				provider.getConfiguration().getMessageService(), 
				new ExecutionContext() {
					@Override
					public MetricInstance getMetricInstance(String id) {
						return executionContext.getMetricInstance(id);
					}
					@Override
					public boolean isDebug() {
						return executionContext.isDebug();
					}
					@Override
					public TransactionContext getTransactionContext() {
						return executionContext.getTransactionContext();
					}
					@Override
					public ServiceContext getServiceContext() {
						return executionContext.getServiceContext();
					}
					@Override
					public SecurityContext getSecurityContext() {
						return executionContext.getSecurityContext();
					}
					@Override
					public EventTarget getEventTarget() {
						// we explicitly do not send an event target
						// websockets are meant for high volume message transfers, having the "message" service show up as a root service event entry every time generates a _lot_ of logs
						// if (in the future) we do want _some_ events to pass, we can update the serviceruntime to recognize some setting so it does not log certain root services
						return null;
					}
				}, 
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
			logger.error("Could not process websocket message", e);
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
