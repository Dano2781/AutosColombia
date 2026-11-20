package co.autoscolombia.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PaymentServiceTest {
    private final PaymentService service = new PaymentService(null);

    @Test
    void acceptsCashPaymentWithoutReference() {
        assertDoesNotThrow(() -> service.validatePaymentInput(new BigDecimal("120000.00"), "EFECTIVO", ""));
    }

    @Test
    void rejectsNonPositiveOrOversizedAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validatePaymentInput(BigDecimal.ZERO, "EFECTIVO", null));
        assertThrows(IllegalArgumentException.class,
                () -> service.validatePaymentInput(new BigDecimal("1000000000000.00"), "EFECTIVO", null));
    }

    @Test
    void requiresReferenceForCardAndTransfer() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validatePaymentInput(new BigDecimal("120000.00"), "TARJETA", "  "));
        assertDoesNotThrow(() -> service.validatePaymentInput(new BigDecimal("120000.00"), "TRANSFERENCIA", "TRX-123"));
    }

    @Test
    void renewsTheDayAfterCurrentOrFutureEndDate() {
        LocalDate paymentDate = LocalDate.of(2026, 11, 18);
        assertEquals(LocalDate.of(2026, 11, 19),
                service.renewalStart(LocalDate.of(2026, 11, 18), paymentDate));
        assertEquals(LocalDate.of(2026, 12, 6),
                service.renewalStart(LocalDate.of(2026, 12, 5), paymentDate));
    }

    @Test
    void renewsFromPaymentDateAfterExpirationAndCreatesThirtyDayPeriod() {
        LocalDate paymentDate = LocalDate.of(2026, 11, 18);
        LocalDate start = service.renewalStart(LocalDate.of(2026, 11, 17), paymentDate);

        assertEquals(paymentDate, start);
        assertEquals(LocalDate.of(2026, 12, 17), service.renewalEnd(start));
    }

    @Test
    void rejectsPaymentForInactiveVehicleBeforeCreatingMonthlyMembership() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PaymentService paymentService = new PaymentService(jdbc);
        when(jdbc.queryForList(contains("FROM vehiculo"), eq("ABC123"))).thenReturn(java.util.List.of(
                java.util.Map.of("id_vehiculo", 1L, "estado_vehiculo", "INACTIVO", "estado_usuario", "ACTIVO")));

        assertThrows(IllegalStateException.class,
                () -> paymentService.registerPayment("ABC123", new BigDecimal("120000.00"), "EFECTIVO", null, "supervisor"));
    }

    @Test
    void refusesCancellationWhenTheMonthlyMembershipHasAnOpenMovement() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PaymentService paymentService = new PaymentService(jdbc);
        when(jdbc.queryForList(contains("FROM pago"), eq(8L))).thenReturn(java.util.List.of(
                java.util.Map.of("id_mensualidad", 5L, "estado", "PAGADO", "placa", "ABC123")));
        when(jdbc.queryForObject(contains("FROM movimiento"), eq(Integer.class), eq(5L))).thenReturn(1);

        assertThrows(IllegalStateException.class,
                () -> paymentService.cancelPayment(8L, "Registro duplicado", "supervisor"));
    }
}
