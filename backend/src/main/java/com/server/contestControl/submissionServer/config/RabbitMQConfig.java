package com.server.contestControl.submissionServer.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitMQConfig {

    public static final String SUBMISSION_QUEUE = "submissionQueue";
    public static final String RESULT_QUEUE     = "resultQueue";

    public static final String SUBMISSION_EXCHANGE = "submissionExchange";
    public static final String RESULT_EXCHANGE     = "resultExchange";

    public static final String SUBMISSION_ROUTING_KEY = "submissionRoutingKey";
    public static final String RESULT_ROUTING_KEY     = "resultRoutingKey";


    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jackson2JsonMessageConverter());
        return rabbitTemplate;
    }

    @Bean
    public Queue submissionQueue() {
        return new Queue(SUBMISSION_QUEUE, true); // durable queue
    }

    @Bean
    public DirectExchange submissionExchange() {
        return new DirectExchange(SUBMISSION_EXCHANGE);
    }

    @Bean
    public Binding submissionBinding(Queue submissionQueue, DirectExchange submissionExchange) {
        return BindingBuilder.bind(submissionQueue)
                .to(submissionExchange)
                .with(SUBMISSION_ROUTING_KEY);
    }

    @Bean
    public Queue resultQueue() {
        return new Queue(RESULT_QUEUE, true);
    }

    @Bean
    public DirectExchange resultExchange() {
        return new DirectExchange(RESULT_EXCHANGE);
    }

    @Bean
    public Binding resultBinding(Queue resultQueue, DirectExchange resultExchange) {
        return BindingBuilder.bind(resultQueue)
                .to(resultExchange)
                .with(RESULT_ROUTING_KEY);
    }
}
