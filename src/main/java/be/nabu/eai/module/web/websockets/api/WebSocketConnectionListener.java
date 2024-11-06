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

package be.nabu.eai.module.web.websockets.api;

import java.util.List;

import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.validation.constraints.NotNull;

import be.nabu.libs.authentication.api.Device;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.types.api.KeyValuePair;

public interface WebSocketConnectionListener {
	public void message(@WebParam(name = "webSocketId") @NotNull String webSocketId, @WebParam(name = "webSocketInstanceId") @NotNull String websocketInstanceId, @WebParam(name = "webApplicationId") @NotNull String webApplicationId, @WebParam(name = "path") @NotNull String path, @WebParam(name = "token") Token token, @WebParam(name = "device") Device device, @WebParam(name = "ip") @NotNull String ip, @WebParam(name = "host") @NotNull String host, @WebParam(name = "port") @NotNull Integer port, @WebParam(name = "pathValues") List<KeyValuePair> pathValues, @WebParam(name = "configuration") Object object);
	@WebResult(name = "denied")
	public Boolean connected(@WebParam(name = "webSocketId") @NotNull String webSocketId, @WebParam(name = "webSocketInstanceId") @NotNull String websocketInstanceId, @WebParam(name = "webApplicationId") @NotNull String webApplicationId, @WebParam(name = "path") @NotNull String path, @WebParam(name = "token") Token token, @WebParam(name = "device") Device device, @WebParam(name = "ip") @NotNull String ip, @WebParam(name = "host") @NotNull String host, @WebParam(name = "port") @NotNull Integer port, @WebParam(name = "pathValues") List<KeyValuePair> pathValues, @WebParam(name = "configuration") Object object);
	public void disconnected(@WebParam(name = "webSocketId") @NotNull String webSocketId, @WebParam(name = "webSocketInstanceId") @NotNull String websocketInstanceId, @WebParam(name = "webApplicationId") @NotNull String webApplicationId, @WebParam(name = "path") @NotNull String path, @WebParam(name = "token") Token token, @WebParam(name = "device") Device device, @WebParam(name = "ip") @NotNull String ip, @WebParam(name = "host") @NotNull String host, @WebParam(name = "port") @NotNull Integer port, @WebParam(name = "pathValues") List<KeyValuePair> pathValues, @WebParam(name = "configuration") Object object);
}
