package org.unwind.account;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
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
    public Queue accountQueue() {
        return QueueBuilder.durable(Messaging.Q_ACCOUNT).build();
    }

    @Bean
    public Binding bindDebit(Queue accountQueue, TopicExchange exchange) {
        return BindingBuilder.bind(accountQueue).to(exchange).with(Messaging.RK_DEBIT);
    }

    @Bean
    public Binding bindCredit(Queue accountQueue, TopicExchange exchange) {
        return BindingBuilder.bind(accountQueue).to(exchange).with(Messaging.RK_CREDIT);
    }

    @Bean
    public Binding bindRefund(Queue accountQueue, TopicExchange exchange) {
        return BindingBuilder.bind(accountQueue).to(exchange).with(Messaging.RK_REFUND);
    }

    @Bean
    public Binding bindReverse(Queue accountQueue, TopicExchange exchange) {
        return BindingBuilder.bind(accountQueue).to(exchange).with(Messaging.RK_REVERSE_CREDIT);
    }

    @Bean
    public MessageConverter jsonConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("org.unwind.*");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(converter);
        return template;
    }
}