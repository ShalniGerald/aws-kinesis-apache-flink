package com.example.fraud.util;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public class SnsPublisher {

    private static final SnsClient snsClient = SnsClient.builder()
            .region(Region.US_EAST_1) // Change region if needed
            .build();

    private static final String TOPIC_ARN = "arn:aws:sns:us-east-1:011528266190:FraudAlerts"; // Replace with your SNS topic ARN

    public static void publishAlert(String message) {
        PublishRequest request = PublishRequest.builder()
                .topicArn(TOPIC_ARN)
                .message(message)
                .subject("FRAUD ALERT")
                .build();

        snsClient.publish(request);
    }
}
