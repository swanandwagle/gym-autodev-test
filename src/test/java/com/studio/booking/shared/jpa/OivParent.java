package com.studio.booking.shared.jpa;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "oiv_parent")
public class OivParent {

    @Id
    public UUID id = UUID.randomUUID();

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "parent_id")
    public List<OivChild> children = new ArrayList<>();
}
