package com.pm.patientservice.kafka;

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import patient.events.PatientEvent;

@Slf4j
@Service
public class KafkaConsumer {

    @KafkaListener(topics = "patient", groupId = "patient-service")
    public void consumeEvent(ConsumerRecord<String, byte[]> record) {
        String key = record.key();
        byte[] value = record.value();

        log.info("Received record from topic 'patient' [key={}, partition={}, offset={}, valueBytes={}]",
                key, record.partition(), record.offset(), value.length);

        try {
            PatientEvent event = PatientEvent.parseFrom(value);
            log.info("Decoded PatientEvent: patientId={}, name={}, email={}, eventType={}",
                    event.getPatientId(),
                    event.getName(),
                    event.getEmail(),
                    event.getEventType());
        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to deserialize PatientEvent: {}", e.getMessage(), e);
        }
    }
}
