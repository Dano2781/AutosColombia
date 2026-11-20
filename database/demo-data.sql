INSERT INTO usuario(tipo_documento, numero_documento, nombre_completo, telefono, email)
VALUES
('CC','100000001','María Fernanda Pérez','3000000001','maria@example.com'),
('CC','100000002','Carlos Andrés Gómez','3000000002','carlos@example.com')
ON CONFLICT (numero_documento) DO NOTHING;

INSERT INTO vehiculo(id_usuario, placa, tipo, marca, color)
SELECT u.id_usuario, v.placa, v.tipo, v.marca, v.color
FROM (VALUES
    ('100000001','ABC123','AUTOMOVIL','Mazda','Rojo'),
    ('100000002','XYZ789','MOTOCICLETA','Yamaha','Negro')
) AS v(documento, placa, tipo, marca, color)
JOIN usuario u ON u.numero_documento = v.documento
ON CONFLICT (placa) DO NOTHING;

INSERT INTO mensualidad(id_vehiculo, fecha_inicio, fecha_fin, estado)
SELECT v.id_vehiculo, CURRENT_DATE - 10, CURRENT_DATE + 20, 'ACTIVA'
FROM vehiculo v WHERE v.placa = 'ABC123'
AND NOT EXISTS (SELECT 1 FROM mensualidad m WHERE m.id_vehiculo=v.id_vehiculo AND m.estado='ACTIVA');
INSERT INTO mensualidad(id_vehiculo, fecha_inicio, fecha_fin, estado)
SELECT v.id_vehiculo, CURRENT_DATE - 5, CURRENT_DATE + 25, 'ACTIVA'
FROM vehiculo v WHERE v.placa = 'XYZ789'
AND NOT EXISTS (SELECT 1 FROM mensualidad m WHERE m.id_vehiculo=v.id_vehiculo AND m.estado='ACTIVA');

INSERT INTO celda(codigo, tipo, estado) VALUES
('A-12','AUTOMOVIL','LIBRE'),('M-01','MOTOCICLETA','LIBRE'),('X-01','MIXTA','LIBRE')
ON CONFLICT (codigo) DO NOTHING;


INSERT INTO operario(nombres, usuario_acceso, password_hash, rol, estado) VALUES
('Operario de Acceso','operario','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','OPERARIO','ACTIVO'),
('Supervisor General','supervisor','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','SUPERVISOR','ACTIVO')
ON CONFLICT (usuario_acceso) DO NOTHING;

INSERT INTO mensualidad(id_vehiculo, fecha_inicio, fecha_fin, estado)
SELECT v.id_vehiculo, CURRENT_DATE + 21, CURRENT_DATE + 50, 'ACTIVA'
FROM vehiculo v
WHERE v.placa = 'ABC123'
  AND NOT EXISTS (
      SELECT 1 FROM mensualidad m
      WHERE m.id_vehiculo = v.id_vehiculo AND m.fecha_inicio = CURRENT_DATE + 21
  );

INSERT INTO pago(id_mensualidad, id_operario_registro, fecha_hora_pago, valor, medio_pago, referencia, estado)
SELECT m.id_mensualidad, o.id_operario, CURRENT_TIMESTAMP, 120000.00, 'TRANSFERENCIA', 'TRX-DEMO-ABC123', 'PAGADO'
FROM mensualidad m
JOIN vehiculo v ON v.id_vehiculo = m.id_vehiculo
JOIN operario o ON o.usuario_acceso = 'supervisor'
WHERE v.placa = 'ABC123' AND m.fecha_inicio = CURRENT_DATE + 21
ON CONFLICT (id_mensualidad) DO NOTHING;
