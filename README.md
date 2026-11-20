# Autos Colombia Iteración 3

Aplicación web para la gestión operativa del parqueadero Autos Colombia. Esta entrega integra las tres iteraciones: control de entrada y salida, gestión de usuarios y celdas, y gestión de pagos de mensualidades.

## Configuración de PostgreSQL

1. Cree una base de datos llamada `autos_colombia`.
2. Ejecute primero `database/schema.sql` y después `database/demo-data.sql` sobre esa base de datos.
3. Configure las variables de entorno antes de iniciar la aplicación:

DB_URL=jdbc:postgresql://localhost:5433/autos_colombia
DB_USER=postgres
DB_PASSWORD=postgres

Puede cambiar el puerto, usuario y contraseña según la instalación local de PostgreSQL.

Usuarios de demostración:

| Rol                | Usuario      | Contraseña |
| Operario de acceso | `operario`   | `Acceso123!` |
| Supervisor         | `supervisor` | `Supervisor123!` |

## Gestión de pagos

El módulo **Pagos** está disponible exclusivamente para el supervisor. Permite buscar pagos, registrar una mensualidad de treinta días para un vehículo activo, consultar e imprimir el comprobante, y anular un pago cuando la mensualidad no tenga un movimiento de vehículo abierto.

Los pagos admiten efectivo, tarjeta o transferencia. Para tarjeta y transferencia se exige una referencia. La información de pagos, mensualidades y auditoría se guarda dentro de una transacción PostgreSQL para evitar registros parciales.