package com.vivekkrishnan.fitbook.controller;

import com.vivekkrishnan.fitbook.dto.NotificationRequestDTO;
import com.vivekkrishnan.fitbook.dto.NotificationResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

// The mock service must treat a repeated idempotencyKey as a harmless duplicate.
class MockNotificationControllerTest {

    private static NotificationRequestDTO request(String key) {
        return new NotificationRequestDTO(key, "kai@example.com",
                NotificationRequestDTO.TYPE_BOOKING_CONFIRMED, "hello");
    }

    @Test
    void sameKeyTwiceStoresOnceAndSecondIsDuplicate() {
        MockNotificationController controller = new MockNotificationController(0, false);

        ResponseEntity<NotificationResponseDTO> first = controller.receive(request("BOOKED-1"));
        ResponseEntity<NotificationResponseDTO> second = controller.receive(request("BOOKED-1"));

        assertThat(first.getBody().status()).isEqualTo(NotificationResponseDTO.STATUS_ACCEPTED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().status()).isEqualTo(NotificationResponseDTO.STATUS_DUPLICATE);
        assertThat(controller.list()).hasSize(1);
    }

    @Test
    void differentKeysAreBothStored() {
        MockNotificationController controller = new MockNotificationController(0, false);

        controller.receive(request("BOOKED-1"));
        controller.receive(request("CANCELLED-1"));

        assertThat(controller.list()).hasSize(2);
    }

    @Test
    void failModeReturns503AndStoresNothing() {
        MockNotificationController controller = new MockNotificationController(0, true);

        ResponseEntity<NotificationResponseDTO> response = controller.receive(request("BOOKED-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(controller.list()).isEmpty();
    }
}
