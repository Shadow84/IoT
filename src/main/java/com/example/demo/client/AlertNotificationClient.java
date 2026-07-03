package com.example.demo.client;

import com.example.demo.dto.AlertNotificationPayload;
import feign.Headers;
import feign.RequestLine;

public interface AlertNotificationClient {

    @RequestLine("POST")
    @Headers("Content-Type: application/json")
    void send(AlertNotificationPayload payload);
}
