package org.millburn.kioskserver;

import com.nimbusds.jose.shaded.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WebSocketHandler extends TextWebSocketHandler {
    private static JdbcTemplate jt;

    public WebSocketHandler(JdbcTemplate jt) {
        this.jt = jt;
    }

    public static ArrayList<WebSocketSession> sessions = new ArrayList<>();
    public static final Logger logger = LogManager.getLogger(WebSocketHandler.class);

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        // Handle incoming messages here
        String receivedMessage = (String) message.getPayload();
        // Process the message and send a response if needed
        try {
            session.sendMessage(new TextMessage("Received: " + receivedMessage));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Perform actions when a new WebSocket connection is established
        sessions.add(session);
        System.out.println("New connection: " + session.getId() + " | sessions: " + sessions.size());
        try {
            session.sendMessage(new TextMessage(new Gson().toJson(getRecords())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // Perform actions when a WebSocket connection is closed
        System.out.println("Connection closed: " + status.getReason());
        sessions.remove(session);
    }

    public static void broadcast(String message) {
        for (WebSocketSession session : sessions) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                logger.error("Failed to send message to session " + session.getId(), e);
            }
        }
    }

    public static List<Records> getRecords() {
        String sql = "SELECT * FROM record ORDER BY num DESC LIMIT 20";
        try {
            List<Map<String, Object>> rows = jt.queryForList(sql);
            List<Records> records = rows.stream().map(row -> new Records(
                    (int) row.get("num"),
                    (int) row.get("id"),
                    (int) row.get("prev_status"),
                    (int) row.get("new_status"),
                    row.get("date").toString(),
                    row.get("kiosk_name").toString()
            )).toList();

            return records;
        } catch (Exception e) {
            logger.error("Failed to get records", e);
            throw new RuntimeException(e);
        }
    }
}