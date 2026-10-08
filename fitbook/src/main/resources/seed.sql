DELETE FROM appointments;
DELETE FROM availability_slots;
DELETE FROM trainers;
DELETE FROM services;
DELETE FROM users;

-- Seed passwords (plaintext, for your reference only, never store these):
--   kai.smith@example.com      -> customer1
--   lloyd.garmadon@example.com -> trainer1
--   cole.brookstone@example.com -> trainer2
INSERT INTO users (user_id, name, email, password_hash, role) VALUES
  (1, 'Kai Smith', 'kai.smith@example.com', '$2a$10$kD0uc8AaI0LZiKYmYSDQWeIqYux8o4WQXQjCAmlT61Hk0SnnTAVce', 'CUSTOMER'),
  (2, 'Lloyd Garmadon', 'lloyd.garmadon@example.com', '$2a$10$MMJAX75YQGg6mNleAMdrTeMkMnPAJafECC1pWSdqLDT/N5BEMtVq2', 'TRAINER'),
  (3, 'Cole Brookestone', 'cole.brookstone@example.com', '$2a$10$SXpN6Qalojey7DlM4kM75eIIen2odDXDQZGDJB9Uwr4mR7f7OUhCK', 'TRAINER');

INSERT INTO services (service_id, service_name, description, duration_minutes, price) VALUES
  (1, 'Weight training', 'Weight Room Session', 60, 45.00),
  (2, 'Pilates', 'Pilates Class', 45, 20.00);

INSERT INTO trainers (trainer_id, user_id, service_id) VALUES
  (1, 2, 1),
  (2, 3, 2);

INSERT INTO availability_slots (slot_id, trainer_id, service_id, start_time, end_time, status) VALUES
  (1, 1, 1, '2026-09-25 09:00:00', '2026-12-25 10:00:00', 'OPEN'),
  (2, 2, 2, '2026-09-25 11:00:00', '2026-10-25 11:45:00', 'OPEN'),
  (3, 1, 1, '2026-09-26 09:00:00', '2026-11-26 10:00:00', 'BOOKED');

INSERT INTO appointments (appointment_id, slot_id, customer_id, service_id, status) VALUES
  (1, 3, 1, 1, 'BOOKED');
