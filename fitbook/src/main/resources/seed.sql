DELETE FROM appointments;
DELETE FROM availability_slots;
DELETE FROM trainers;
DELETE FROM services;
DELETE FROM users;

INSERT INTO users (user_id, name, email, role) VALUES
  (1, 'Kai Smith', 'kai.smith@example.com', 'CUSTOMER'),
  (2, 'Lloyd Garmadon', 'lloyd.garmadon@example.com', 'TRAINER'),
  (3, 'Cole Brookestone', 'cole.brookstone@example.com', 'TRAINER');

INSERT INTO services (service_id, service_name, description, duration_minutes, price) VALUES
  (1, 'Weight training', 'Weight Room Session', 60, 45.00),
  (2, 'Pilates', 'Pilates Class', 45, 20.00);

INSERT INTO trainers (trainer_id, user_id, service_id) VALUES
  (1, 2, 1),
  (2, 3, 2);

INSERT INTO availability_slots (slot_id, trainer_id, service_id, start_time, end_time, status) VALUES
  (1, 1, 1, '2026-09-25 09:00:00', '2026-09-25 10:00:00', 'OPEN'),
  (2, 2, 2, '2026-09-25 11:00:00', '2026-09-25 11:45:00', 'OPEN'),
  (3, 1, 1, '2026-09-26 09:00:00', '2026-09-26 10:00:00', 'BOOKED');

INSERT INTO appointments (appointment_id, slot_id, customer_id, service_id, status) VALUES
  (1, 3, 1, 1, 'BOOKED');
