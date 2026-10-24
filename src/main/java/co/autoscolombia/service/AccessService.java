package co.autoscolombia.service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessService {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public AccessService(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc; this.named = named;
    }

    public String normalizePlate(String plate) {
        return plate == null ? "" : plate.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    public Map<String,Object> checkVehicle(String plate) {
        String p = normalizePlate(plate);
        List<Map<String,Object>> vehicles = jdbc.queryForList("""
            SELECT v.id_vehiculo, v.placa, v.tipo, v.marca, v.color, v.estado AS estado_vehiculo,
                   u.nombre_completo, m.id_mensualidad, m.fecha_inicio, m.fecha_fin, m.estado AS estado_mensualidad
            FROM vehiculo v JOIN usuario u ON u.id_usuario=v.id_usuario
            LEFT JOIN mensualidad m ON m.id_vehiculo=v.id_vehiculo
            WHERE v.placa=? ORDER BY m.fecha_fin DESC
            """, p);
        if (vehicles.isEmpty()) return Map.of("permitido", false, "motivo", "La placa no está registrada.");
        Map<String,Object> v = vehicles.get(0);
        if (!"ACTIVO".equals(v.get("estado_vehiculo"))) return Map.of("permitido", false, "motivo", "El vehículo está inactivo.", "vehiculo", v);
        List<Map<String,Object>> current = jdbc.queryForList("""
            SELECT id_mensualidad, fecha_inicio, fecha_fin, estado FROM mensualidad
            WHERE id_vehiculo=? AND estado='ACTIVA' AND fecha_inicio<=CURRENT_DATE AND fecha_fin>=CURRENT_DATE
            ORDER BY fecha_fin DESC
            """, v.get("id_vehiculo"));
        if (current.isEmpty()) return Map.of("permitido", false, "motivo", "No existe una mensualidad activa y vigente.", "vehiculo", v);
        Integer open = jdbc.queryForObject("""
            SELECT COUNT(*) FROM movimiento mo JOIN mensualidad me ON me.id_mensualidad=mo.id_mensualidad
            WHERE me.id_vehiculo=? AND mo.estado='ABIERTA'
            """, Integer.class, v.get("id_vehiculo"));
        if (open != null && open > 0) return Map.of("permitido", false, "motivo", "El vehículo ya tiene un movimiento abierto.", "vehiculo", v);
        List<Map<String,Object>> cells = jdbc.queryForList("""
            SELECT id_celda,codigo,tipo,estado FROM celda
            WHERE estado='LIBRE' AND (tipo='MIXTA' OR tipo=? OR (?='CAMIONETA' AND tipo='AUTOMOVIL'))
            ORDER BY codigo
            """, v.get("tipo"), v.get("tipo"));
        if (cells.isEmpty()) return Map.of("permitido", false, "motivo", "No hay celdas libres compatibles.", "vehiculo", v);
        return Map.of("permitido", true, "motivo", "Acceso permitido.", "vehiculo", v, "mensualidad", current.get(0), "celdas", cells);
    }

    @Transactional
    public long registerEntry(String plate, long cellId, String operatorUsername) {
        String p = normalizePlate(plate);
        Map<String,Object> check = checkVehicle(p);
        if (!Boolean.TRUE.equals(check.get("permitido"))) throw new IllegalStateException((String) check.get("motivo"));
        Map<String,Object> v = (Map<String,Object>) check.get("vehiculo");
        Map<String,Object> m = (Map<String,Object>) check.get("mensualidad");
        Map<String,Object> cell = jdbc.queryForMap("SELECT * FROM celda WHERE id_celda=? FOR UPDATE", cellId);
        if (!"LIBRE".equals(cell.get("estado")) || !compatible(String.valueOf(v.get("tipo")), String.valueOf(cell.get("tipo"))))
            throw new IllegalStateException("La celda no está libre o no es compatible.");
        Long operatorId = jdbc.queryForObject("SELECT id_operario FROM operario WHERE usuario_acceso=? AND estado='ACTIVO'", Long.class, operatorUsername);
        if (operatorId == null) throw new IllegalStateException("El operario no está activo.");
        jdbc.update("INSERT INTO movimiento (id_mensualidad,id_celda,id_operario_entrada,fecha_hora_entrada,estado) VALUES (?,?,?, ?, 'ABIERTA')",
                m.get("id_mensualidad"), cellId, operatorId, Timestamp.valueOf(LocalDateTime.now()));
        long movementId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("UPDATE celda SET estado='OCUPADA' WHERE id_celda=? AND estado='LIBRE'", cellId);
        audit(operatorId, "REGISTRAR_ENTRADA", "ACEPTADA", "Movimiento " + movementId + ", placa " + p);
        return movementId;
    }

    @Transactional
    public void registerExit(String plate, String operatorUsername) {
        String p = normalizePlate(plate);
        List<Map<String,Object>> rows = jdbc.queryForList("""
            SELECT mo.id_movimiento, mo.id_celda, mo.id_mensualidad, me.id_vehiculo
            FROM movimiento mo JOIN mensualidad me ON me.id_mensualidad=mo.id_mensualidad
            JOIN vehiculo v ON v.id_vehiculo=me.id_vehiculo
            WHERE v.placa=? AND mo.estado='ABIERTA' FOR UPDATE
            """, p);
        if (rows.isEmpty()) throw new IllegalStateException("No existe un movimiento abierto para esa placa.");
        Long operatorId = jdbc.queryForObject("SELECT id_operario FROM operario WHERE usuario_acceso=? AND estado='ACTIVO'", Long.class, operatorUsername);
        if (operatorId == null) throw new IllegalStateException("El operario no está activo.");
        Map<String,Object> row = rows.get(0);
        jdbc.update("UPDATE movimiento SET estado='CERRADA', fecha_hora_salida=?, id_operario_salida=? WHERE id_movimiento=? AND estado='ABIERTA'",
                Timestamp.valueOf(LocalDateTime.now()), operatorId, row.get("id_movimiento"));
        jdbc.update("UPDATE celda SET estado='LIBRE' WHERE id_celda=?", row.get("id_celda"));
        audit(operatorId, "REGISTRAR_SALIDA", "ACEPTADA", "Movimiento " + row.get("id_movimiento") + ", placa " + p);
    }

    public List<Map<String,Object>> movements(String plate, String state, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("""
            SELECT mo.id_movimiento,v.placa,v.tipo,c.codigo AS celda,mo.fecha_hora_entrada,mo.fecha_hora_salida,
                   mo.estado,o.nombres AS operario_entrada,os.nombres AS operario_salida
            FROM movimiento mo JOIN mensualidad me ON me.id_mensualidad=mo.id_mensualidad
            JOIN vehiculo v ON v.id_vehiculo=me.id_vehiculo JOIN celda c ON c.id_celda=mo.id_celda
            JOIN operario o ON o.id_operario=mo.id_operario_entrada
            LEFT JOIN operario os ON os.id_operario=mo.id_operario_salida WHERE 1=1
            """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (plate != null && !plate.isBlank()) { sql.append(" AND v.placa=:plate"); params.addValue("plate", normalizePlate(plate)); }
        if (state != null && !state.isBlank()) { sql.append(" AND mo.estado=:state"); params.addValue("state", state); }
        if (from != null) { sql.append(" AND mo.fecha_hora_entrada>=:fromDate"); params.addValue("fromDate", from.atStartOfDay()); }
        if (to != null) { sql.append(" AND mo.fecha_hora_entrada<:toDate"); params.addValue("toDate", to.plusDays(1).atStartOfDay()); }
        sql.append(" ORDER BY mo.fecha_hora_entrada DESC");
        return named.queryForList(sql.toString(), params);
    }

    private boolean compatible(String vehicleType, String cellType) {
        return "MIXTA".equals(cellType) || vehicleType.equals(cellType) ||
                ("CAMIONETA".equals(vehicleType) && "AUTOMOVIL".equals(cellType));
    }

    private void audit(Long operatorId, String operation, String result, String detail) {
        jdbc.update("INSERT INTO auditoria (id_operario,operacion,resultado,detalle,fecha_hora) VALUES (?,?,?,?,?)",
                operatorId, operation, result, detail, Timestamp.valueOf(LocalDateTime.now()));
    }
}
