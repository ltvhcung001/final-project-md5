package com.omnichannel.notification.repository;

import com.omnichannel.notification.document.DeliveryLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DeliveryLogRepository extends MongoRepository<DeliveryLog, String> {

    boolean existsByEventIdAndStatus(String eventId, DeliveryLog.Status status);
}
