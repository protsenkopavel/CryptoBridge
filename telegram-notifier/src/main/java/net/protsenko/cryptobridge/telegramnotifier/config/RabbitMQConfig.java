package net.protsenko.cryptobridge.telegramnotifier.config;

import lombok.RequiredArgsConstructor;
import net.protsenko.cryptobridge.telegramnotifier.props.RabbitMQProperties;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RabbitMQConfig {

    private final RabbitMQProperties rabbitMQProperties;

    @Bean
    public Queue arbitrageQueue() {
        return QueueBuilder.durable(rabbitMQProperties.getQueueName()).build();
    }

    @Bean
    public DirectExchange arbitrageExchange() {
        return new DirectExchange(rabbitMQProperties.getExchangeName());
    }

    @Bean
    public Binding arbitrageBinding() {
        return BindingBuilder.bind(arbitrageQueue())
                .to(arbitrageExchange())
                .with(rabbitMQProperties.getRoutingKey());
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }

}
