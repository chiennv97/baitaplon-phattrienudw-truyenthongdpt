/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.kurento.tutorial.groupcall;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.kurento.client.IceCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import javax.print.attribute.HashAttributeSet;

/**
 * 
 * @author Ivan Gracia (izanmail@gmail.com)
 * @since 4.3.1
 */
public class CallHandler extends TextWebSocketHandler {

  private static final Logger log = LoggerFactory.getLogger(CallHandler.class);

  private static final Gson gson = new GsonBuilder().create();

  @Autowired
  private RoomManager roomManager;

  @Autowired
  private UserRegistry registry;

  private HashSet<String> listDisable = new HashSet<String>();

  @Override
  public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    final JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);

    final UserSession user = registry.getBySession(session);
    ConcurrentHashMap<String, UserSession> users = registry.getAll();
    if (user != null) {
      log.debug("Incoming message from user '{}': {}", user.getName(), jsonMessage);
    } else {
      log.debug("Incoming message from new user: {}", jsonMessage);
    }

    switch (jsonMessage.get("id").getAsString()) {
      case "joinRoom":
        joinRoom(jsonMessage, session);
        break;
      case "receiveVideoFrom":
        final String senderName = jsonMessage.get("sender").getAsString();
        final UserSession sender = registry.getByName(senderName);
        final String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
        user.receiveVideoFrom(sender, sdpOffer);
        if(listDisable.contains(senderName)){
          user.disableAudio(sender);
        }
//        for(String key : listDisable){
//          System.out.println("Day la key "+key);
//          UserSession sender3 = registry.getByName(key);
//          users.get(name).disableAudio(sender);
//        }
        break;
      case "leaveRoom":
        String leaver = jsonMessage.get("leaver").getAsString();
        JsonObject leaveMsg = new JsonObject();
        leaveMsg.addProperty("id", "leave");
        leaveMsg.addProperty("user", leaver);
        users.get(leaveMsg);
        leaveRoom(users.get(leaver));
        break;
      case "disableSound":
        String senderName1 = jsonMessage.get("disabler").getAsString();
        listDisable.add(senderName1);
        for (String key: users.keySet()) {
          UserSession sender1 = registry.getByName(senderName1);
          users.get(key).disableAudio(sender1);
        }

        break;
      case "disableVideo":
        String senderName2 = jsonMessage.get("disabler").getAsString();
        listDisable.remove(senderName2);
        for (String key: users.keySet()) {
          UserSession sender2 = registry.getByName(senderName2);
          users.get(key).disableVideo(sender2);
        }
        break;
      case "onIceCandidate":
        JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();

        if (user != null) {
          IceCandidate cand = new IceCandidate(candidate.get("candidate").getAsString(),
              candidate.get("sdpMid").getAsString(), candidate.get("sdpMLineIndex").getAsInt());
          user.addCandidate(cand, jsonMessage.get("name").getAsString());
        }
        break;
      default:
        break;
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    UserSession user = registry.removeBySession(session);
    roomManager.getRoom(user.getRoomName()).leave(user);
  }

  private void joinRoom(JsonObject params, WebSocketSession session) throws IOException {
    final String roomName = params.get("room").getAsString();
    final String name = params.get("name").getAsString();
    log.info("PARTICIPANT {}: trying to join room {}", name, roomName);

    Room room = roomManager.getRoomAndHost(roomName, name);
    final UserSession user = room.join(name, session);
    registry.register(user);
//    ConcurrentHashMap<String, UserSession> users = registry.getAll();
//
//    for(String key : listDisable){
//      System.out.println("Day la key "+key);
//      UserSession sender = registry.getByName(key);
//      users.get(name).disableAudio(sender);
//    }
  }

  private void leaveRoom(UserSession user) throws IOException {
    final Room room = roomManager.getRoom(user.getRoomName());
    room.leave(user);
    if (room.getParticipants().isEmpty()) {
      roomManager.removeRoom(room);
    }
    listDisable.remove(user.getRoomName());
  }


}
