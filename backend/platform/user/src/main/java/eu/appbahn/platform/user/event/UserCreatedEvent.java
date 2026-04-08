package eu.appbahn.platform.user.event;

import java.util.UUID;

public record UserCreatedEvent(UUID userId, String email) {}
