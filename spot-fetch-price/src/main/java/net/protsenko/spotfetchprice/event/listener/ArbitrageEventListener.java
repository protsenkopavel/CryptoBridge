package net.protsenko.spotfetchprice.event.listener;

import lombok.RequiredArgsConstructor;
import net.protsenko.spotfetchprice.dto.ArbitrageOpportunityFoundEvent;
import net.protsenko.spotfetchprice.props.RabbitMQProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ArbitrageEventListener {

    private final RabbitTemplate rabbitTemplate;

    private final RabbitMQProperties rabbitMQProperties;

    @EventListener
    public void handleArbitrageEvent(ArbitrageOpportunityFoundEvent event) {
        rabbitTemplate.convertAndSend(rabbitMQProperties.getExchangeName(), rabbitMQProperties.getRoutingKey(), event);
    }

}
