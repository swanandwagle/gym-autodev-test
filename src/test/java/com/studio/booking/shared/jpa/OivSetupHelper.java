package com.studio.booking.shared.jpa;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class OivSetupHelper {

    @Autowired
    OivParentRepo repo;

    @Transactional
    public UUID createParentWithChild() {
        OivParent parent = new OivParent();
        parent.children.add(new OivChild());
        return repo.save(parent).id;
    }
}
