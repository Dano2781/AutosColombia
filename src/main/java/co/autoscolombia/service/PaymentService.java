package co.autoscolombia.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PaymentService {
    private static final BigDecimal MAX_PAYMENT_VALUE = new BigDecimal("999999999999.99");
    private static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    private final JdbcTemplate jdbc;

    public PaymentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void validatePaymentInput(BigDecimal amount, String paymentMethod, String reference) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_PAYMENT_VALUE) > 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("El valor del pago debe ser mayor que cero y admitir máximo dos decimales.");
        }
        String method = normalizePaymentMethod(paymentMethod);
        if (!"EFECTIVO".equals(method) && !StringUtils.hasText(reference)) {
            throw new IllegalArgumentException("La referencia es obligatoria para pagos con tarjeta o transferencia.");
        }
        if (reference != null && reference.trim().length() > 60) {
            throw new IllegalArgumentException("La referencia admite máximo 60 caracteres.");
        }
    }

    public LocalDate renewalStart(LocalDate latestEndDate, LocalDate paymentDate) {
        if (paymentDate == null) {
            throw new IllegalArgumentException("La fecha de pago es obligatoria.");
        }
        if (latestEndDate != null && !latestEndDate.isBefore(paymentDate)) {
            return latestEndDate.plusDays(1);
        }
        return paymentDate;
    }

    public LocalDate renewalEnd(LocalDate startDate) {
        if (startDate == null) {
            throw new IllegalArgumentException("La fecha inicial es obligatoria.");
        }
        return startDate.plusDays(29);
    }

    public List<Map<String, Object>> paymentVehicles(String plate) {
        String normalizedPlate = normalizePlate(plate);
        return jdbc.queryForList("""
            SELECT v.id_vehiculo, v.placa, v.tipo, v.marca, v.color,
                   u.nombre_completo, u.numero_documento,
                   MAX(m.fecha_fin) AS ultimo_vencimiento
            FROM vehiculo v
            JOIN usuario u ON u.id_usuario = v.id_usuario
            LEFT JOIN mensualidad m ON m.id_vehiculo = v.id_vehiculo
            WHERE v.estado = 'ACTIVO' AND u.estado = 'ACTIVO'
              AND (? = '' OR v.placa = ?)
            GROUP BY v.id_vehiculo, u.id_usuario
            ORDER BY v.placa
            """, normalizedPlate, normalizedPlate);
    }

    public List<Map<String, Object>> payments(String plate, String state, String paymentMethod,
                                               LocalDate fromDate, LocalDate toDate) {
        StringBuilder sql = new StringBuilder("""
            SELECT p.id_pago, p.fecha_hora_pago, p.valor, p.medio_pago, p.referencia, p.estado,
                   p.fecha_hora_anulacion, p.motivo_anulacion, v.placa, u.nombre_completo,
                   m.fecha_inicio, m.fecha_fin, o.nombres AS operario_registro
            FROM pago p
            JOIN mensualidad m ON m.id_mensualidad = p.id_mensualidad
            JOIN vehiculo v ON v.id_vehiculo = m.id_vehiculo
            JOIN usuario u ON u.id_usuario = v.id_usuario
            JOIN operario o ON o.id_operario = p.id_operario_registro
            WHERE 1 = 1
            """);
        List<Object> parameters = new ArrayList<>();
        if (StringUtils.hasText(plate)) {
            sql.append(" AND v.placa = ?");
            parameters.add(normalizePlate(plate));
        }
        if ("PAGADO".equals(state) || "ANULADO".equals(state)) {
            sql.append(" AND p.estado = ?");
            parameters.add(state);
        }
        if ("EFECTIVO".equals(paymentMethod) || "TARJETA".equals(paymentMethod) || "TRANSFERENCIA".equals(paymentMethod)) {
            sql.append(" AND p.medio_pago = ?");
            parameters.add(paymentMethod);
        }
        if (fromDate != null) {
            sql.append(" AND p.fecha_hora_pago >= ?");
            parameters.add(fromDate.atStartOfDay());
        }
        if (toDate != null) {
            sql.append(" AND p.fecha_hora_pago < ?");
            parameters.add(toDate.plusDays(1).atStartOfDay());
        }
        sql.append(" ORDER BY p.fecha_hora_pago DESC");
        return jdbc.queryForList(sql.toString(), parameters.toArray());
    }

    public Map<String, Object> payment(long paymentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT p.id_pago, p.id_mensualidad, p.fecha_hora_pago, p.valor, p.medio_pago, p.referencia,
                   p.estado, p.fecha_hora_anulacion, p.motivo_anulacion, v.placa, v.tipo,
                   u.nombre_completo, u.numero_documento, m.fecha_inicio, m.fecha_fin,
                   o.nombres AS operario_registro, oa.nombres AS operario_anulacion
            FROM pago p
            JOIN mensualidad m ON m.id_mensualidad = p.id_mensualidad
            JOIN vehiculo v ON v.id_vehiculo = m.id_vehiculo
            JOIN usuario u ON u.id_usuario = v.id_usuario
            JOIN operario o ON o.id_operario = p.id_operario_registro
            LEFT JOIN operario oa ON oa.id_operario = p.id_operario_anulacion
            WHERE p.id_pago = ?
            """, paymentId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("El pago seleccionado no existe.");
        }
        return rows.get(0);
    }

    @Transactional
    public long registerPayment(String plate, BigDecimal amount, String paymentMethod, String reference,
                                String operatorUsername) {
        validatePaymentInput(amount, paymentMethod, reference);
        String normalizedPlate = normalizePlate(plate);
        List<Map<String, Object>> vehicles = jdbc.queryForList("""
            SELECT v.id_vehiculo, v.estado AS estado_vehiculo, u.estado AS estado_usuario
            FROM vehiculo v JOIN usuario u ON u.id_usuario = v.id_usuario
            WHERE v.placa = ? FOR UPDATE
            """, normalizedPlate);
        if (vehicles.isEmpty()) {
            throw new IllegalArgumentException("La placa no está registrada.");
        }
        Map<String, Object> vehicle = vehicles.get(0);
        if (!"ACTIVO".equals(vehicle.get("estado_vehiculo")) || !"ACTIVO".equals(vehicle.get("estado_usuario"))) {
            throw new IllegalStateException("Solo se pueden registrar pagos para vehículos y usuarios activos.");
        }
        long vehicleId = ((Number) vehicle.get("id_vehiculo")).longValue();
        LocalDate paymentDate = LocalDate.now(COLOMBIA);
        LocalDate latestEndDate = jdbc.queryForObject("""
            SELECT MAX(fecha_fin) FROM mensualidad
            WHERE id_vehiculo = ? AND estado <> 'SUSPENDIDA'
            """, LocalDate.class, vehicleId);
        LocalDate startDate = renewalStart(latestEndDate, paymentDate);
        LocalDate endDate = renewalEnd(startDate);
        long operatorId = activeOperatorId(operatorUsername);
        long membershipId = jdbc.queryForObject("""
            INSERT INTO mensualidad(id_vehiculo, fecha_inicio, fecha_fin, estado)
            VALUES (?, ?, ?, 'ACTIVA') RETURNING id_mensualidad
            """, Long.class, vehicleId, startDate, endDate);
        long paymentId = jdbc.queryForObject("""
            INSERT INTO pago(id_mensualidad, id_operario_registro, fecha_hora_pago, valor,
                             medio_pago, referencia, estado)
            VALUES (?, ?, ?, ?, ?, ?, 'PAGADO') RETURNING id_pago
            """, Long.class, membershipId, operatorId, Timestamp.valueOf(LocalDateTime.now(COLOMBIA)), amount,
                normalizePaymentMethod(paymentMethod), blankToNull(reference));
        audit(operatorId, "REGISTRAR_PAGO", "ACEPTADA", "Pago " + paymentId + ", placa " + normalizedPlate);
        return paymentId;
    }

    @Transactional
    public void cancelPayment(long paymentId, String reason, String operatorUsername) {
        if (!StringUtils.hasText(reason) || reason.trim().length() > 250) {
            throw new IllegalArgumentException("El motivo de anulación es obligatorio y admite máximo 250 caracteres.");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT p.id_mensualidad, p.estado, v.placa
            FROM pago p
            JOIN mensualidad m ON m.id_mensualidad = p.id_mensualidad
            JOIN vehiculo v ON v.id_vehiculo = m.id_vehiculo
            WHERE p.id_pago = ? FOR UPDATE
            """, paymentId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("El pago seleccionado no existe.");
        }
        Map<String, Object> payment = rows.get(0);
        if (!"PAGADO".equals(payment.get("estado"))) {
            throw new IllegalStateException("El pago ya fue anulado.");
        }
        long membershipId = ((Number) payment.get("id_mensualidad")).longValue();
        Integer openMovements = jdbc.queryForObject("""
            SELECT COUNT(*) FROM movimiento
            WHERE id_mensualidad = ? AND estado = 'ABIERTA'
            """, Integer.class, membershipId);
        if (openMovements != null && openMovements > 0) {
            throw new IllegalStateException("No se puede anular el pago porque el vehículo tiene un movimiento abierto.");
        }
        long operatorId = activeOperatorId(operatorUsername);
        Timestamp cancellationTime = Timestamp.valueOf(LocalDateTime.now(COLOMBIA));
        jdbc.update("""
            UPDATE pago SET estado = 'ANULADO', fecha_hora_anulacion = ?, id_operario_anulacion = ?,
                            motivo_anulacion = ?
            WHERE id_pago = ? AND estado = 'PAGADO'
            """, cancellationTime, operatorId, reason.trim(), paymentId);
        jdbc.update("UPDATE mensualidad SET estado = 'SUSPENDIDA' WHERE id_mensualidad = ?", membershipId);
        audit(operatorId, "ANULAR_PAGO", "ACEPTADA", "Pago " + paymentId + ", placa " + payment.get("placa"));
    }

    private long activeOperatorId(String username) {
        List<Long> ids = jdbc.query("""
            SELECT id_operario FROM operario
            WHERE usuario_acceso = ? AND estado = 'ACTIVO'
            """, (rs, rowNumber) -> rs.getLong(1), username);
        if (ids.isEmpty()) {
            throw new IllegalStateException("El supervisor autenticado no está registrado o está inactivo.");
        }
        return ids.get(0);
    }

    private void audit(long operatorId, String operation, String result, String detail) {
        jdbc.update("""
            INSERT INTO auditoria(id_operario, operacion, resultado, detalle, fecha_hora)
            VALUES (?, ?, ?, ?, ?)
            """, operatorId, operation, result, detail, Timestamp.valueOf(LocalDateTime.now(COLOMBIA)));
    }

    private String normalizePlate(String plate) {
        return plate == null ? "" : plate.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizePaymentMethod(String paymentMethod) {
        String method = paymentMethod == null ? "" : paymentMethod.trim().toUpperCase(Locale.ROOT);
        if (!"EFECTIVO".equals(method) && !"TARJETA".equals(method) && !"TRANSFERENCIA".equals(method)) {
            throw new IllegalArgumentException("Seleccione un medio de pago válido.");
        }
        return method;
    }
}
