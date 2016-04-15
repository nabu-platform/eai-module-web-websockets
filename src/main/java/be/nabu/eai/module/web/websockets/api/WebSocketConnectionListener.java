package be.nabu.eai.module.web.websockets.api;

import javax.jws.WebParam;

import be.nabu.libs.authentication.api.Token;

public interface WebSocketConnectionListener {
	public void connected(@WebParam(name = "webApplicationId") String webApplicationId, @WebParam(name = "path") String path, @WebParam(name = "token") Token token);
	public void disconnected(@WebParam(name = "webApplicationId") String webApplicationId, @WebParam(name = "path") String path, @WebParam(name = "token") Token token);
}
