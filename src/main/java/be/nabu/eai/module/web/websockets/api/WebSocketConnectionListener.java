package be.nabu.eai.module.web.websockets.api;

import java.util.List;

import javax.jws.WebParam;
import javax.validation.constraints.NotNull;

import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.types.api.KeyValuePair;

public interface WebSocketConnectionListener {
	public void message(@WebParam(name = "webApplicationId") @NotNull String webApplicationId, @WebParam(name = "path") @NotNull String path, @WebParam(name = "token") Token token, @WebParam(name = "host") @NotNull String host, @WebParam(name = "port") @NotNull Integer port, @WebParam(name = "pathValues") List<KeyValuePair> pathValues, @WebParam(name = "configuration") Object object);
	public void connected(@WebParam(name = "webApplicationId") @NotNull String webApplicationId, @WebParam(name = "path") @NotNull String path, @WebParam(name = "token") Token token, @WebParam(name = "host") @NotNull String host, @WebParam(name = "port") @NotNull Integer port, @WebParam(name = "pathValues") List<KeyValuePair> pathValues, @WebParam(name = "configuration") Object object);
	public void disconnected(@WebParam(name = "webApplicationId") @NotNull String webApplicationId, @WebParam(name = "path") @NotNull String path, @WebParam(name = "token") Token token, @WebParam(name = "host") @NotNull String host, @WebParam(name = "port") @NotNull Integer port, @WebParam(name = "pathValues") List<KeyValuePair> pathValues, @WebParam(name = "configuration") Object object);
}
