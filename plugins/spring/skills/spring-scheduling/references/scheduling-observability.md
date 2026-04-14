---
title: Spring Scheduling Observability
description: >-
  Reference for logging, metrics, and lightweight visibility of app-local scheduled tasks.
---

Use this reference when scheduling is already correct and the remaining blocker is operational visibility.

## Logging Rule

- log task start, success, and failure with a stable task identity
- include enough context to correlate repeated failures or overlap

## Metrics Rule

- emit duration metrics per task
- emit success and failure counters per task
- keep tags stable and low-cardinality

## Visibility Rule

- expose `/actuator/scheduledtasks` as the default production visibility surface when the application already uses Spring Boot Actuator
- expose locally registered task identities when operations need to know what is scheduled in the running JVM
- keep business identifiers separate from raw framework objects when surfacing task inventory

Actuator note:

- `scheduledtasks` is not exposed by default; add it deliberately to the management exposure list and apply the same security policy as other operator endpoints

## Tracing Rule

- scheduled work usually starts a new trace boundary rather than continuing an inbound request trace
- keep one span or observation per scheduled execution when tracing is enabled in the application stack
