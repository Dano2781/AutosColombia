USE autos_colombia;
-- Las contraseñas BCrypt son generadas para las credenciales de demostración del README.
INSERT INTO usuario(tipo_documento,numero_documento,nombre_completo,telefono,email) VALUES
('CC','100000001','María Fernanda Pérez','3000000001','maria@example.com'),
('CC','100000002','Carlos Andrés Gómez','3000000002','carlos@example.com');
INSERT INTO vehiculo(id_usuario,placa,tipo,marca,color) VALUES
(1,'ABC123','AUTOMOVIL','Mazda','Rojo'),
(2,'XYZ789','MOTOCICLETA','Yamaha','Negro');
INSERT INTO mensualidad(id_vehiculo,fecha_inicio,fecha_fin,estado) VALUES
(1,CURRENT_DATE - INTERVAL 10 DAY,CURRENT_DATE + INTERVAL 20 DAY,'ACTIVA'),
(2,CURRENT_DATE - INTERVAL 5 DAY,CURRENT_DATE + INTERVAL 25 DAY,'ACTIVA');
INSERT INTO celda(codigo,tipo,estado) VALUES
('A-12','AUTOMOVIL','LIBRE'),('M-01','MOTOCICLETA','LIBRE'),('X-01','MIXTA','LIBRE');
-- Passwords de demo: Acceso123! y Supervisor123! (BCrypt)
INSERT INTO operario(nombres,usuario_acceso,password_hash,rol,estado) VALUES
('Operario de Acceso','operario','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','OPERARIO','ACTIVO'),
('Supervisor General','supervisor','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','SUPERVISOR','ACTIVO');
