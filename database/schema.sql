CREATE DATABASE IF NOT EXISTS autos_colombia CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE autos_colombia;

CREATE TABLE IF NOT EXISTS usuario (
 id_usuario BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 tipo_documento VARCHAR(10) NOT NULL, numero_documento VARCHAR(20) NOT NULL UNIQUE,
 nombre_completo VARCHAR(120) NOT NULL, telefono VARCHAR(25), email VARCHAR(120),
 estado ENUM('ACTIVO','INACTIVO') NOT NULL DEFAULT 'ACTIVO'
);
CREATE TABLE IF NOT EXISTS vehiculo (
 id_vehiculo BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 id_usuario BIGINT UNSIGNED NOT NULL, placa CHAR(6) NOT NULL UNIQUE,
 tipo ENUM('AUTOMOVIL','MOTOCICLETA','CAMIONETA','OTRO') NOT NULL,
 marca VARCHAR(50), color VARCHAR(30), estado ENUM('ACTIVO','INACTIVO') NOT NULL DEFAULT 'ACTIVO',
 CONSTRAINT fk_vehiculo_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
);
CREATE TABLE IF NOT EXISTS mensualidad (
 id_mensualidad BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, id_vehiculo BIGINT UNSIGNED NOT NULL,
 fecha_inicio DATE NOT NULL, fecha_fin DATE NOT NULL,
 estado ENUM('ACTIVA','VENCIDA','SUSPENDIDA') NOT NULL DEFAULT 'ACTIVA',
 CONSTRAINT ck_mensualidad_fechas CHECK (fecha_fin >= fecha_inicio),
 CONSTRAINT fk_mensualidad_vehiculo FOREIGN KEY (id_vehiculo) REFERENCES vehiculo(id_vehiculo),
 INDEX idx_mensualidad_vigencia (id_vehiculo, estado, fecha_inicio, fecha_fin)
);
CREATE TABLE IF NOT EXISTS celda (
 id_celda BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, codigo VARCHAR(10) NOT NULL UNIQUE,
 tipo ENUM('AUTOMOVIL','MOTOCICLETA','MIXTA') NOT NULL,
 estado ENUM('LIBRE','OCUPADA','INACTIVA') NOT NULL DEFAULT 'LIBRE'
);
CREATE TABLE IF NOT EXISTS operario (
 id_operario BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, nombres VARCHAR(120) NOT NULL,
 usuario_acceso VARCHAR(50) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL,
 rol ENUM('OPERARIO','SUPERVISOR') NOT NULL DEFAULT 'OPERARIO',
 estado ENUM('ACTIVO','INACTIVO') NOT NULL DEFAULT 'ACTIVO'
);
CREATE TABLE IF NOT EXISTS movimiento (
 id_movimiento BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, id_mensualidad BIGINT UNSIGNED NOT NULL,
 id_celda BIGINT UNSIGNED NOT NULL, id_operario_entrada BIGINT UNSIGNED NOT NULL,
 id_operario_salida BIGINT UNSIGNED NULL, fecha_hora_entrada DATETIME NOT NULL,
 fecha_hora_salida DATETIME NULL, estado ENUM('ABIERTA','CERRADA') NOT NULL DEFAULT 'ABIERTA',
 movimiento_abierto_mensualidad BIGINT UNSIGNED GENERATED ALWAYS AS
  (CASE WHEN estado='ABIERTA' THEN id_mensualidad ELSE NULL END) STORED,
 CONSTRAINT ck_salida_posterior CHECK (fecha_hora_salida IS NULL OR fecha_hora_salida >= fecha_hora_entrada),
 CONSTRAINT fk_movimiento_mensualidad FOREIGN KEY (id_mensualidad) REFERENCES mensualidad(id_mensualidad),
 CONSTRAINT fk_movimiento_celda FOREIGN KEY (id_celda) REFERENCES celda(id_celda),
 CONSTRAINT fk_movimiento_operario_entrada FOREIGN KEY (id_operario_entrada) REFERENCES operario(id_operario),
 CONSTRAINT fk_movimiento_operario_salida FOREIGN KEY (id_operario_salida) REFERENCES operario(id_operario),
 CONSTRAINT uq_movimiento_abierto_por_mensualidad UNIQUE (movimiento_abierto_mensualidad),
 INDEX idx_movimiento_celda_estado (id_celda, estado), INDEX idx_movimiento_entrada (fecha_hora_entrada)
);
CREATE TABLE IF NOT EXISTS auditoria (
 id_auditoria BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 id_operario BIGINT UNSIGNED NULL, operacion VARCHAR(50) NOT NULL,
 resultado ENUM('ACEPTADA','RECHAZADA') NOT NULL, detalle VARCHAR(500),
 fecha_hora DATETIME NOT NULL,
 CONSTRAINT fk_auditoria_operario FOREIGN KEY (id_operario) REFERENCES operario(id_operario)
);
