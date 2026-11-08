package co.autoscolombia.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminService {
    private static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    private final JdbcTemplate jdbc;

    public AdminService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> users(String search, String state) {
        String term = search == null ? "" : search.trim();
        String status = state == null ? "" : state.trim();
        return jdbc.queryForList("""
            SELECT u.id_usuario, u.tipo_documento, u.numero_documento, u.nombre_completo,
                   u.telefono, u.email, u.estado, COUNT(v.id_vehiculo) AS cantidad_vehiculos
            FROM usuario u LEFT JOIN vehiculo v ON v.id_usuario=u.id_usuario
            WHERE (?='' OR LOWER(u.nombre_completo) LIKE LOWER(?) OR u.numero_documento LIKE ?)
              AND (?='' OR u.estado=?)
            GROUP BY u.id_usuario ORDER BY u.nombre_completo
            """, term, "%" + term + "%", "%" + term + "%", status, status);
    }

    public Map<String, Object> user(long id) {
        return jdbc.queryForMap("SELECT * FROM usuario WHERE id_usuario=?", id);
    }

    public List<Map<String, Object>> vehiclesForUser(long id) {
        return jdbc.queryForList("""
            SELECT id_vehiculo, placa, tipo, marca, color, estado
            FROM vehiculo WHERE id_usuario=? ORDER BY placa
            """, id);
    }

    @Transactional
    public long createUser(String documentType, String documentNumber, String fullName,
                           String phone, String email, String operatorUsername) {
        validateUser(documentType, documentNumber, fullName, phone, email);
        Long operatorId = operatorId(operatorUsername);
        Long id;
        try {
            id = jdbc.queryForObject("""
                INSERT INTO usuario(tipo_documento,numero_documento,nombre_completo,telefono,email,estado)
                VALUES (?,?,?,?,?,'ACTIVO') RETURNING id_usuario
                """, Long.class, documentType.trim().toUpperCase(Locale.ROOT), documentNumber.trim(),
                    fullName.trim(), blankToNull(phone), blankToNull(email));
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("Ya existe un usuario con ese número de documento.");
        }
        audit(operatorId, "REGISTRAR_USUARIO", "ACEPTADA", "Usuario " + documentNumber.trim());
        return id;
    }

    @Transactional
    public void updateUser(long id, String documentType, String documentNumber, String fullName,
                           String phone, String email, String operatorUsername) {
        validateUser(documentType, documentNumber, fullName, phone, email);
        Long operatorId = operatorId(operatorUsername);
        int count;
        try {
            count = jdbc.update("""
                UPDATE usuario SET tipo_documento=?, numero_documento=?, nombre_completo=?, telefono=?, email=?,
                    fecha_actualizacion=CURRENT_TIMESTAMP
                WHERE id_usuario=?
                """, documentType.trim().toUpperCase(Locale.ROOT), documentNumber.trim(), fullName.trim(),
                    blankToNull(phone), blankToNull(email), id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("El número de documento ya pertenece a otro usuario.");
        }
        if (count != 1) throw new IllegalArgumentException("El usuario seleccionado no existe.");
        audit(operatorId, "ACTUALIZAR_USUARIO", "ACEPTADA", "Usuario " + documentNumber.trim());
    }

    @Transactional
    public void changeUserState(long id, String newState, String operatorUsername) {
        if (!"ACTIVO".equals(newState) && !"INACTIVO".equals(newState)) throw new IllegalArgumentException("Estado de usuario no válido.");
        Long operatorId = operatorId(operatorUsername);
        Map<String, Object> user = jdbc.queryForMap("SELECT * FROM usuario WHERE id_usuario=? FOR UPDATE", id);
        if ("INACTIVO".equals(newState)) {
            Integer open = jdbc.queryForObject("""
                SELECT COUNT(*) FROM movimiento mo
                JOIN mensualidad me ON me.id_mensualidad=mo.id_mensualidad
                JOIN vehiculo v ON v.id_vehiculo=me.id_vehiculo
                WHERE v.id_usuario=? AND mo.estado='ABIERTA'
                """, Integer.class, id);
            if (open != null && open > 0)
                throw new IllegalStateException("No se puede inactivar el usuario porque tiene un vehículo dentro del parqueadero.");
        }
        jdbc.update("UPDATE usuario SET estado=?, fecha_actualizacion=CURRENT_TIMESTAMP WHERE id_usuario=?", newState, id);
        audit(operatorId, "CAMBIAR_ESTADO_USUARIO", "ACEPTADA", user.get("numero_documento") + " -> " + newState);
    }

    public List<Map<String, Object>> cells(String search, String type, String state) {
        String term = search == null ? "" : search.trim();
        String cellType = type == null ? "" : type.trim();
        String cellState = state == null ? "" : state.trim();
        return jdbc.queryForList("""
            SELECT c.id_celda,c.codigo,c.tipo,c.estado,
                   CASE WHEN c.estado='OCUPADA' THEN v.placa ELSE NULL END AS placa_actual
            FROM celda c
            LEFT JOIN movimiento mo ON mo.id_celda=c.id_celda AND mo.estado='ABIERTA'
            LEFT JOIN mensualidad me ON me.id_mensualidad=mo.id_mensualidad
            LEFT JOIN vehiculo v ON v.id_vehiculo=me.id_vehiculo
            WHERE (?='' OR LOWER(c.codigo) LIKE LOWER(?))
              AND (?='' OR c.tipo=?) AND (?='' OR c.estado=?)
            ORDER BY c.codigo
            """, term, "%" + term + "%", cellType, cellType, cellState, cellState);
    }

    public Map<String, Object> cell(long id) {
        return jdbc.queryForMap("SELECT * FROM celda WHERE id_celda=?", id);
    }

    @Transactional
    public long createCell(String code, String type, String operatorUsername) {
        validateCell(code, type);
        Long operatorId = operatorId(operatorUsername);
        Long id;
        try {
            id = jdbc.queryForObject("""
                INSERT INTO celda(codigo,tipo,estado) VALUES (?,?, 'LIBRE') RETURNING id_celda
                """, Long.class, code.trim().toUpperCase(Locale.ROOT), type);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("Ya existe una celda con ese código.");
        }
        audit(operatorId, "REGISTRAR_CELDA", "ACEPTADA", "Celda " + code.trim().toUpperCase(Locale.ROOT));
        return id;
    }

    @Transactional
    public void updateCell(long id, String type, String operatorUsername) {
        if (!"AUTOMOVIL".equals(type) && !"MOTOCICLETA".equals(type) && !"MIXTA".equals(type))
            throw new IllegalArgumentException("Tipo de celda no válido.");
        Long operatorId = operatorId(operatorUsername);
        Map<String, Object> cell = jdbc.queryForMap("SELECT * FROM celda WHERE id_celda=? FOR UPDATE", id);
        if (!"LIBRE".equals(cell.get("estado")))
            throw new IllegalStateException("Solo se puede modificar el tipo de una celda que esté LIBRE.");
        ensureCellNotOccupied(id, String.valueOf(cell.get("estado")));
        jdbc.update("UPDATE celda SET tipo=?, fecha_actualizacion=CURRENT_TIMESTAMP WHERE id_celda=?",
                type, id);
        audit(operatorId, "ACTUALIZAR_CELDA", "ACEPTADA", cell.get("codigo") + " -> " + type);
    }

    @Transactional
    public void changeCellState(long id, String newState, String operatorUsername) {
        if (!"LIBRE".equals(newState) && !"INACTIVA".equals(newState))
            throw new IllegalArgumentException("El estado administrativo permitido es LIBRE o INACTIVA.");
        Long operatorId = operatorId(operatorUsername);
        Map<String, Object> cell = jdbc.queryForMap("SELECT * FROM celda WHERE id_celda=? FOR UPDATE", id);
        ensureCellNotOccupied(id, String.valueOf(cell.get("estado")));
        jdbc.update("UPDATE celda SET estado=?, fecha_actualizacion=CURRENT_TIMESTAMP WHERE id_celda=?", newState, id);
        audit(operatorId, "CAMBIAR_ESTADO_CELDA", "ACEPTADA", cell.get("codigo") + " -> " + newState);
    }

    public List<Map<String, Object>> cellHistory(long id) {
        return jdbc.queryForList("""
            SELECT mo.id_movimiento,v.placa,mo.fecha_hora_entrada,mo.fecha_hora_salida,mo.estado,
                   oi.nombres AS operario_entrada, os.nombres AS operario_salida
            FROM movimiento mo JOIN mensualidad me ON me.id_mensualidad=mo.id_mensualidad
            JOIN vehiculo v ON v.id_vehiculo=me.id_vehiculo
            JOIN operario oi ON oi.id_operario=mo.id_operario_entrada
            LEFT JOIN operario os ON os.id_operario=mo.id_operario_salida
            WHERE mo.id_celda=? ORDER BY mo.fecha_hora_entrada DESC
            """, id);
    }

    private void ensureCellNotOccupied(long id, String state) {
        Integer open = jdbc.queryForObject("SELECT COUNT(*) FROM movimiento WHERE id_celda=? AND estado='ABIERTA'", Integer.class, id);
        if ("OCUPADA".equals(state) || (open != null && open > 0))
            throw new IllegalStateException("No se puede modificar una celda ocupada o con un movimiento abierto.");
    }

    private Long operatorId(String username) {
        List<Long> ids = jdbc.query("SELECT id_operario FROM operario WHERE usuario_acceso=? AND estado='ACTIVO'",
                (rs, row) -> rs.getLong(1), username);
        if (ids.isEmpty()) throw new IllegalStateException("El operario autenticado no está registrado o está inactivo.");
        return ids.get(0);
    }

    private void audit(Long operatorId, String operation, String result, String detail) {
        jdbc.update("INSERT INTO auditoria(id_operario,operacion,resultado,detalle,fecha_hora) VALUES (?,?,?,?,?)",
                operatorId, operation, result, detail, Timestamp.valueOf(LocalDateTime.now(COLOMBIA)));
    }

    private void validateUser(String type, String document, String name, String phone, String email) {
        if (!StringUtils.hasText(type) || !StringUtils.hasText(document) || !StringUtils.hasText(name))
            throw new IllegalArgumentException("Tipo y número de documento y nombre completo son obligatorios.");
        if (document.trim().length() > 20 || name.trim().length() > 120)
            throw new IllegalArgumentException("El documento o el nombre supera la longitud permitida.");
        if (type.trim().length() > 10) throw new IllegalArgumentException("Tipo de documento no válido.");
        if (phone != null && phone.trim().length() > 25) throw new IllegalArgumentException("El teléfono admite máximo 25 caracteres.");
        if (email != null && email.trim().length() > 120) throw new IllegalArgumentException("El correo admite máximo 120 caracteres.");
        if (StringUtils.hasText(email) && !email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new IllegalArgumentException("Ingrese un correo electrónico válido.");
    }

    private void validateCell(String code, String type) {
        if (!StringUtils.hasText(code) || code.trim().length() > 10)
            throw new IllegalArgumentException("El código de celda es obligatorio y admite máximo 10 caracteres.");
        if (!"AUTOMOVIL".equals(type) && !"MOTOCICLETA".equals(type) && !"MIXTA".equals(type))
            throw new IllegalArgumentException("Seleccione un tipo de celda válido.");
    }

    private String blankToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
}
