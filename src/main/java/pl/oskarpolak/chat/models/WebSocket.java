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

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

@Component
@EnableWebSocket
@Log
public class WebSocket extends TextWebSocketHandler implements WebSocketConfigurer {
    private List<UserModel> sessions = new LinkedList<>();
    private Deque<String> lastTenMessages = new ArrayDeque<>();

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

    private Optional<UserModel> findUserByNick(String nickname){
        return sessions.stream()
                .filter(s -> s.getNickname() != null && s.getNickname().equals(nickname))
                .findFirst();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        UserModel sender = findUserBySession(session);
        if(sender.getNickname() == null){
            if(!isNameFree(message.getPayload())){
                sender.getSession().sendMessage(new TextMessage("Ten nick jest zajęty"));
                return;
            }
            sender.setNickname(message.getPayload()); // jego nick = wiadomosc ktora wyslal
            sender.getSession().sendMessage(new TextMessage("Ustawiłeś swój nick!"));

            if(sender.getNickname().equals("oskar")){
                sender.setAdmin(true);
                sender.getSession().sendMessage(new TextMessage("Zostałeś adminem"));
            }

            sendMessageArchiveToUser(sender);
            return;
        }

        checkIfMessageIsCommand(sender, message);

        if(sender.isMute()){
            sender.getSession().sendMessage(new TextMessage("Nie mozesz pisac"));
            return;
        }

        for (UserModel user : sessions) {
             user.getSession()
                     .sendMessage(new TextMessage(sender.getNickname() + ": " + message.getPayload()));
        }

        addMessageToArchive(sender.getNickname() + ": " + message.getPayload());
    }

    private void checkIfMessageIsCommand(UserModel sender, TextMessage message) throws IOException {
        if(!sender.isAdmin()){
            return;
        }

        if(message.getPayload().startsWith("/mute")){
            String nick = message.getPayload().split(" ")[1];
            Optional<UserModel> userToMuteOptional = findUserByNick(nick);
            UserModel userToMute;
            if(!userToMuteOptional.isPresent()){
                sender.getSession().sendMessage(new TextMessage("Podany nick nie istnieje"));
                return;
            }

            userToMute = userToMuteOptional.get();
            userToMute.setMute(true);

            sender.getSession().sendMessage(new TextMessage("Zmutowales popranie"));
            userToMute.getSession().sendMessage(new TextMessage("Zostales zmutowany!@!@!@"));
        }
    }

    private void sendMessageArchiveToUser(UserModel sender) throws IOException {
        for (String lastTenMessage : lastTenMessages) {
            sender.getSession().sendMessage(new TextMessage(lastTenMessage));
        }
    }

    private boolean isNameFree(String nickname){
        return sessions
                .stream()
                .filter(s -> s.getNickname() != null)
                .noneMatch(s -> s.getNickname().equals(nickname));
    }

    private void addMessageToArchive(String message){
        if(lastTenMessages.size() >= 10){
            lastTenMessages.pollFirst();
        }

        lastTenMessages.addLast(message);
    }
}
