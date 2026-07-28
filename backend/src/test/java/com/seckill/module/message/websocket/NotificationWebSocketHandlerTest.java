package com.seckill.module.message.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationWebSocketHandlerTest {

    private NotificationWebSocketHandler handler;

    @Mock
    private WebSocketSession session1;
    @Mock
    private WebSocketSession session2;

    @BeforeEach
    void setUp() {
        handler = new NotificationWebSocketHandler();
    }

    @Test
    void afterConnectionEstablished_addsSession() throws Exception {
        handler.afterConnectionEstablished(session1);
        assertThat(handler.getSessionCount()).isEqualTo(1);
    }

    @Test
    void afterConnectionClosed_removesSession() throws Exception {
        handler.afterConnectionEstablished(session1);
        handler.afterConnectionEstablished(session2);
        handler.afterConnectionClosed(session1, CloseStatus.NORMAL);

        assertThat(handler.getSessionCount()).isEqualTo(1);
    }

    @Test
    void broadcast_sendsToAllSessions() throws Exception {
        when(session1.isOpen()).thenReturn(true);
        when(session2.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(session1);
        handler.afterConnectionEstablished(session2);

        handler.broadcast("hello");

        verify(session1).sendMessage(new TextMessage("hello"));
        verify(session2).sendMessage(new TextMessage("hello"));
    }

    @Test
    void broadcast_sessionFails_otherSessionsStillReceive() throws Exception {
        when(session1.isOpen()).thenReturn(true);
        when(session2.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(session1);
        handler.afterConnectionEstablished(session2);

        doThrow(new IOException("connection lost")).when(session1).sendMessage(new TextMessage("hello"));

        handler.broadcast("hello");

        verify(session2).sendMessage(new TextMessage("hello"));
    }

    @Test
    void broadcast_closedSession_doesNotSend() throws Exception {
        when(session1.isOpen()).thenReturn(false);
        handler.afterConnectionEstablished(session1);

        handler.broadcast("hello");

        verify(session1, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcast_emptySessionSet_noException() {
        assertThatCode(() -> handler.broadcast("hello"))
                .doesNotThrowAnyException();
    }

    @Test
    void afterConnectionEstablished_duplicateSession_countsOnce() throws Exception {
        handler.afterConnectionEstablished(session1);
        handler.afterConnectionEstablished(session1);

        assertThat(handler.getSessionCount()).isEqualTo(1);
    }
}
