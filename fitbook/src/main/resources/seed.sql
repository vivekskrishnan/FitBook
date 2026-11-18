-- Seed rows use fixed ids with INSERT IGNORE: they are added on the first start and left
-- alone afterwards, so bookings made by users survive restarts. Slot times are relative to
-- the day of the first start.

-- Seed passwords (plaintext, for your reference only, never store these):
--   kai.smith@example.com      -> customer1
--   lloyd.garmadon@example.com -> trainer1
--   cole.brookstone@example.com -> trainer2
INSERT IGNORE INTO users (user_id, name, email, password_hash, role) VALUES
  (1, 'Kai Smith', 'kai.smith@example.com', '$2a$10$kD0uc8AaI0LZiKYmYSDQWeIqYux8o4WQXQjCAmlT61Hk0SnnTAVce', 'CUSTOMER'),
  (2, 'Lloyd Garmadon', 'lloyd.garmadon@example.com', '$2a$10$MMJAX75YQGg6mNleAMdrTeMkMnPAJafECC1pWSdqLDT/N5BEMtVq2', 'TRAINER'),
  (3, 'Cole Brookestone', 'cole.brookstone@example.com', '$2a$10$SXpN6Qalojey7DlM4kM75eIIen2odDXDQZGDJB9Uwr4mR7f7OUhCK', 'TRAINER'),
  (4, 'Nya Smith', 'nya.smith@example.com', '$2a$12$z0k8Vnnuod7f0mEXJXPMFeUZ69oleGBGyOsj2nlvLcVfjnOOfzLPS', 'CUSTOMER')
  ;

INSERT IGNORE INTO services (service_id, service_name, description, duration_minutes, price) VALUES
  (1, 'Weight training', 'Weight Room Session', 60, 45.00),
  (2, 'Pilates', 'Pilates Class', 45, 20.00);

INSERT IGNORE INTO trainers (trainer_id, user_id, service_id) VALUES
  (1, 2, 1),
  (2, 3, 2);

INSERT IGNORE INTO availability_slots (slot_id, trainer_id, service_id, start_time, end_time, status) VALUES
  (1, 1, 1, CURDATE() + INTERVAL 1 DAY + INTERVAL 9 HOUR,  CURDATE() + INTERVAL 1 DAY + INTERVAL 10 HOUR, 'OPEN'),
  (2, 2, 2, CURDATE() + INTERVAL 1 DAY + INTERVAL 11 HOUR, CURDATE() + INTERVAL 1 DAY + INTERVAL 705 MINUTE, 'OPEN'),
  (3, 1, 1, CURDATE() + INTERVAL 2 DAY + INTERVAL 9 HOUR,  CURDATE() + INTERVAL 2 DAY + INTERVAL 10 HOUR, 'BOOKED'),
  (4, 1, 1, CURDATE() + INTERVAL 2 DAY + INTERVAL 14 HOUR, CURDATE() + INTERVAL 2 DAY + INTERVAL 15 HOUR, 'OPEN'),
  (5, 2, 2, CURDATE() + INTERVAL 3 DAY + INTERVAL 10 HOUR, CURDATE() + INTERVAL 3 DAY + INTERVAL 645 MINUTE, 'OPEN'),
  (6, 1, 1, CURDATE() + INTERVAL 3 DAY + INTERVAL 16 HOUR, CURDATE() + INTERVAL 3 DAY + INTERVAL 17 HOUR, 'OPEN');

INSERT IGNORE INTO appointments (appointment_id, slot_id, customer_id, service_id, status) VALUES
  (1, 3, 1, 1, 'BOOKED');
