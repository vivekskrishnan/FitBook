// Shapes returned by the Spring Boot API (/api/**).

export type Role = 'CUSTOMER' | 'TRAINER' | 'ADMIN';

export interface User {
  id: number;
  name: string;
  email: string;
  role: Role;
}

export interface Slot {
  slotId: number;
  trainerId: number;
  trainerName: string;
  serviceId: number;
  serviceName: string;
  startTime: string; // ISO local date-time, e.g. 2026-11-20T09:00:00
  endTime: string;
  price: number;
  status: 'OPEN' | 'BOOKED';
}

export interface SlotsPage {
  slots: Slot[];
  page: number;
  pageSize: number;
  totalCount: number;
  totalPages: number;
}

export interface Option {
  id: number;
  name: string;
}

export interface SlotOptions {
  trainers: Option[];
  services: Option[];
}

export interface Appointment {
  appointmentId: number;
  slotId: number;
  customerId: number;
  trainerName: string;
  serviceName: string;
  startTime: string;
  endTime: string;
  price: number;
  status: 'BOOKED' | 'CANCELLED';
}

export interface FaqAnswer {
  answer: string;
  sources: string[];
}

export interface ReminderSummary {
  found: number;
  sent: number;
  alreadyReminded: number;
  llmWritten: number;
  fallbackUsed: number;
  failed: number;
}
