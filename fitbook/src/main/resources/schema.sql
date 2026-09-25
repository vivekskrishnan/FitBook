DROP TABLE IF EXISTS appointments;
DROP TABLE IF EXISTS availability_slots;
DROP TABLE IF EXISTS trainers;
DROP TABLE IF EXISTS services;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    user_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(150) NOT NULL,
    role        VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role  CHECK (role IN ('CUSTOMER','TRAINER','ADMIN'))
);

CREATE TABLE services (
    service_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_name     VARCHAR(100) NOT NULL,
    description      VARCHAR(255),
    duration_minutes INT NOT NULL,
    price            DECIMAL(8,2) NOT NULL,
    CONSTRAINT ck_services_duration CHECK (duration_minutes > 0),
    CONSTRAINT ck_services_price    CHECK (price >= 0)
);

CREATE TABLE trainers (
    trainer_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    service_id  BIGINT,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_trainers_user_id UNIQUE (user_id),
    CONSTRAINT fk_trainers_user    FOREIGN KEY (user_id)    REFERENCES users(user_id),
    CONSTRAINT fk_trainers_service FOREIGN KEY (service_id) REFERENCES services(service_id)
);

CREATE TABLE availability_slots (
    slot_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    trainer_id  BIGINT NOT NULL,
    service_id  BIGINT NOT NULL,
    start_time  DATETIME NOT NULL,
    end_time    DATETIME NOT NULL,
    status      VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    CONSTRAINT fk_slots_trainer FOREIGN KEY (trainer_id) REFERENCES trainers(trainer_id),
    CONSTRAINT fk_slots_service FOREIGN KEY (service_id) REFERENCES services(service_id),
    CONSTRAINT ck_slots_status  CHECK (status IN ('OPEN','BOOKED')),
    CONSTRAINT ck_slots_time    CHECK (end_time > start_time),
    CONSTRAINT uq_slots_trainer_start UNIQUE (trainer_id, start_time)
);

CREATE TABLE appointments (
    appointment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    slot_id        BIGINT NOT NULL,
    customer_id    BIGINT NOT NULL,
    service_id     BIGINT NOT NULL,
    status         VARCHAR(12) NOT NULL DEFAULT 'AWAITING',
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_appt_slot     FOREIGN KEY (slot_id)     REFERENCES availability_slots(slot_id),
    CONSTRAINT fk_appt_customer FOREIGN KEY (customer_id) REFERENCES users(user_id),
    CONSTRAINT fk_appt_service  FOREIGN KEY (service_id)  REFERENCES services(service_id),
    CONSTRAINT ck_appt_status   CHECK (status IN ('AWAITING','BOOKED','COMPLETE')),
    CONSTRAINT uq_appt_slot     UNIQUE (slot_id)
);
