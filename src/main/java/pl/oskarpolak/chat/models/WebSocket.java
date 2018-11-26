package pl.oskarpolak.chat.models;

import lombok.extern.java.Log;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

@Component
@EnableWebSocket
@Log
public class WebSocket extends TextWebSocketHandler implements WebSocketConfigurer {
    private List<UserModel> sessions = new LinkedList<>();

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry webSocketHandlerRegistry) {
        webSocketHandlerRegistry.addHandler(this, "/chat").setAllowedOrigins("*");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(new UserModel(session));
        log.info("Ktoś połączył się  z naszym socketem");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.removeIf(s -> s.getSession().getId().equals(session.getId()));
        log.info("Ktoś wyszedł");
    }

    private UserModel findUserBySession(WebSocketSession session){
        return sessions.stream()
                .filter(s -> s.getSession().getId().equals(session.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        UserModel sender = findUserBySession(session);
        if(sender.getNickname() == null){
            sender.setNickname(message.getPayload()); // jego nick = wiadomosc ktora wyslal
            sender.getSession().sendMessage(new TextMessage("Ustawiłeś swój nick!"));
            return;
        }

        for (UserModel user : sessions) {
             user.getSession()
                     .sendMessage(new TextMessage(sender.getNickname() + ": " + message.getPayload()));
        }
    }
}
