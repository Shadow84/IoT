package com.example.demo.config;

import com.example.demo.client.AlertNotificationClient;
import feign.Feign;
import feign.Logger;
import feign.slf4j.Slf4jLogger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;
import org.springframework.cloud.openfeign.support.HttpMessageConverterCustomizer;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebhookClientConfig {

    @Value("${alarm.webhook.url}")
    private String webhookUrl;

    @Bean
    public AlertNotificationClient webhookClient(
            ObjectProvider<FeignHttpMessageConverters> converters,
            ObjectProvider<HttpMessageConverterCustomizer> customizers) {
        return Feign.builder()
                .encoder(new SpringEncoder(converters))
                .decoder(new SpringDecoder(converters))
                .logger(new Slf4jLogger(AlertNotificationClient.class))
                .logLevel(Logger.Level.BASIC)
                .target(AlertNotificationClient.class, webhookUrl);
    }
}
