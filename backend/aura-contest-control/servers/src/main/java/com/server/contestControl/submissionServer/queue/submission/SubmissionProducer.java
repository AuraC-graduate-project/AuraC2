package com.server.contestControl.submissionServer.queue.submission;

import com.server.contestControl.submissionServer.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubmissionProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendSubmission(Long submissionId) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SUBMISSION_EXCHANGE,
                RabbitMQConfig.SUBMISSION_ROUTING_KEY,
                submissionId
        );
    }
}