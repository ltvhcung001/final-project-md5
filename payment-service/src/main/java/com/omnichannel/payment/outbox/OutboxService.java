package com.omnichannel.payment.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxService {

    private final OutboxRepository repo;
    private final ObjectMapper mapper;

    public OutboxService(OutboxRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void add(String aggregateId, String routingKey, Object event) {
        try {
            repo.save(new OutboxEvent(aggregateId, routingKey, mapper.writeValueAsString(event)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + event.getClass().getSimpleName(), e);
        }
    }
}
