package org.unwind.ledger;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.unwind.common.Messaging;

@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(Messaging.EXCHANGE, true, false);
    }

    @Bean
    public Queue ledgerQueue() {
        return QueueBuilder.durable(Messaging.Q_LEDGER).build();
    }

    @Bean
    public Binding bindLedger(Queue ledgerQueue, TopicExchange exchange) {
        return BindingBuilder.bind(ledgerQueue).to(exchange).with(Messaging.RK_LEDGER);
    }

    @Bean
    public MessageConverter jsonConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(converter);
        return template;
    }
}