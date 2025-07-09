package com.__25J_323.HarvestPrediction.service;

import com.__25J_323.HarvestPrediction.model.EnvironmentData;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public void sendEnvironmentUpdate(EnvironmentData data) {
        try {
            messagingTemplate.convertAndSend("/topic/environment", data);
        } catch (Exception e) {
            log.error("Error sending environment update via WebSocket", e);
        }
    }
}
