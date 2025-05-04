package com.cts.healthcare.service;

import com.cts.healthcare.entity.Appointment;
import com.cts.healthcare.entity.DoctorAvailability;
import com.cts.healthcare.entity.Status;
import com.cts.healthcare.entity.TimeSlot;
import com.cts.healthcare.entity.User;
import com.cts.healthcare.repository.AppointmentRepository;
import com.cts.healthcare.repository.DoctorAvailabilityRepository;
import com.cts.healthcare.repository.UserRepository;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final UserRepository userRepository;
    private final DoctorAvailabilityService doctorAvailabilityService;

    public AppointmentService(AppointmentRepository appointmentRepository, DoctorAvailabilityRepository availabilityRepository, UserRepository userRepository, DoctorAvailabilityService doctorAvailabilityService) {
        this.appointmentRepository = appointmentRepository;
        this.availabilityRepository = availabilityRepository;
        this.userRepository = userRepository;
        this.doctorAvailabilityService = doctorAvailabilityService;
    }

    public String bookAppointment(Appointment appointment) {
        // Validate if the doctor exists in the Users table
        User doctor = userRepository.findById(appointment.getDoctor().getId())
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found with ID: " + appointment.getDoctor().getId()));

        // Validate if the patient exists in the Users table
        User patient = userRepository.findById(appointment.getPatient().getId())
                .orElseThrow(() -> new IllegalArgumentException("Patient not found with ID: " + appointment.getPatient().getId()));

        // Check if the time slot is blocked by the doctor
        if (doctorAvailabilityService.isTimeSlotBlocked(doctor, appointment.getDate(), appointment.getTimeSlot())) {
            return "The selected time slot is blocked by the doctor.";
        }

        // Check if the time slot exists in the availability table
        Optional<DoctorAvailability> availability = availabilityRepository.findByDoctorIdAndDateAndTimeSlot(
                doctor.getId(), appointment.getDate(), appointment.getTimeSlot()
        ).stream().findFirst();

        if (availability.isPresent()) {
            // If the time slot exists, it is unavailable
            return "The selected time slot is unavailable.";
        } else {
            // If the time slot does not exist, book the appointment and mark it as unavailable
            DoctorAvailability newAvailability = new DoctorAvailability();
            newAvailability.setDoctor(doctor);
            newAvailability.setDate(appointment.getDate());
            newAvailability.setTimeSlot(appointment.getTimeSlot());
            availabilityRepository.save(newAvailability);

            // Save the appointment
            appointment.setDoctor(doctor);
            appointment.setPatient(patient);
            appointment.setStatus(Status.BOOKED);
            appointmentRepository.save(appointment);

            return "Appointment booked successfully!";
        }
    }
    @Transactional
    public String cancelAppointment(Long appointmentId) {
        Optional<Appointment> appointment = appointmentRepository.findByAppointmentId(appointmentId);
        if (appointment.isPresent()) {
            // Update appointment status
            appointment.get().setStatus(Status.CANCELLED);
            appointmentRepository.save(appointment.get());
    
            // Delete availability
            availabilityRepository.deleteByDoctorIdAndDateAndTimeSlot(
                appointment.get().getDoctor().getId(),
                appointment.get().getDate(),
                appointment.get().getTimeSlot()
            );
    
            return "Appointment cancelled successfully!";
        } else {
            return "Appointment not found.";
        }
    }
    
    @Transactional
    public String modifyAppointment(Long appointmentId, LocalDate newDate, TimeSlot newTimeSlot) {
        Optional<Appointment> appointment = appointmentRepository.findById(appointmentId);
        if (appointment.isPresent()) {
            Appointment existingAppointment = appointment.get();
    
            // Delete the old availability
            availabilityRepository.deleteByDoctorIdAndDateAndTimeSlot(
                existingAppointment.getDoctor().getId(),
                existingAppointment.getDate(),
                existingAppointment.getTimeSlot()
            );
    
            // Check if the new time slot is blocked by the doctor
            if (doctorAvailabilityService.isTimeSlotBlocked(existingAppointment.getDoctor(), newDate, newTimeSlot)) {
                return "The selected new time slot is blocked by the doctor.";
            }
    
            // Check if the new time slot is available
            Optional<DoctorAvailability> newAvailability = availabilityRepository.findByDoctorIdAndDateAndTimeSlot(
                existingAppointment.getDoctor().getId(), newDate, newTimeSlot
            ).stream().findFirst();
    
            if (newAvailability.isPresent()) {
                // If the new time slot exists, it is unavailable
                return "The selected new time slot is unavailable.";
            } else {
                // If the new time slot does not exist, mark it as unavailable
                DoctorAvailability updatedAvailability = new DoctorAvailability();
                updatedAvailability.setDoctor(existingAppointment.getDoctor());
                updatedAvailability.setDate(newDate);
                updatedAvailability.setTimeSlot(newTimeSlot);
                availabilityRepository.save(updatedAvailability);
    
                // Save the updated appointment
                existingAppointment.setDate(newDate);
                existingAppointment.setTimeSlot(newTimeSlot);
                appointmentRepository.save(existingAppointment);
    
                return "Appointment modified successfully!";
            }
        } else {
            return "Appointment not found.";
        }
    }
    
    @Transactional
    public String completeAppointment(Long appointmentId) {
        Optional<Appointment> appointment = appointmentRepository.findByAppointmentId(appointmentId);
        if (appointment.isPresent()) {
            // Update appointment status to COMPLETED
            appointment.get().setStatus(Status.COMPLETED);
            appointmentRepository.save(appointment.get());

            return "Appointment marked as completed!";
        } else {
            return "Appointment not found.";
        }
    }
    public List<Appointment> getUpcomingAppointments(Long patientId) {
        return appointmentRepository.findByPatientIdAndStatus(patientId, Status.BOOKED);
    }
    public List<Appointment> getUpcomingAppointmentsForDoctor(Long doctorId) {
        return appointmentRepository.findByDoctorIdAndStatus(doctorId, Status.BOOKED);
    }
    public List<Appointment> getCompletedAppointmentsForPatients(Long patientId) {
        System.out.println(appointmentRepository.findByDoctorIdAndStatus(patientId, Status.COMPLETED));
        return appointmentRepository.findByPatientIdAndStatus(patientId, Status.COMPLETED);
    }
    public List<Appointment> getCompletedAppointmentsForDoctors(Long patientId) {
        System.out.println(appointmentRepository.findByDoctorIdAndStatus(patientId, Status.COMPLETED));

        return appointmentRepository.findByDoctorIdAndStatus(patientId, Status.COMPLETED);
    }

    public Optional<Appointment> giveByAppointmentId(Long id) {
        return appointmentRepository.findByAppointmentId(id);
    }
}
