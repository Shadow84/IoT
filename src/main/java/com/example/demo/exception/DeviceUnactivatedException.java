package com.example.demo.exception;

public class DeviceUnactivatedException extends RuntimeException {

    public DeviceUnactivatedException(Long deviceId) {
        super("Device " + deviceId + " is INACTIVE and cannot accept metrics.");
    }
}
