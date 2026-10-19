package com.engperf.application.metrics;

/**
 * One part of a population's waiting time: a reason the team wrote, the reserved bucket for impeded
 * time nobody named, or the residual of waiting that carried no impediment signal at all.
 */
public record ImpedimentSlice(String key, String label, double hours, double share) {}
