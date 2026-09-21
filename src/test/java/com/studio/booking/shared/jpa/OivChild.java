package com.studio.booking.shared.jpa;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "oiv_child")
public class OivChild {

    @Id
    public UUID id = UUID.randomUUID();
}
