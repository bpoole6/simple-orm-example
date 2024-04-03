package org.example.entity;

import org.example.annotations.Column;
import org.example.annotations.Entity;

import java.sql.Connection;
import java.sql.ResultSet;


@Entity(table = "order_header")
public class OrderHeader extends BaseEntity {

    public OrderHeader(ResultSet rs) {

    }


    @Column(name = "id", conflict = true)
    private String id; //UUID

    @Column(name = "first_name", updatable = false)
    private String firstName;

    @Column
    private String lastName;

    private String postCalculatedFieldThatChanges;

    @Column(updatable = false)
    private Long created_datetime;
    @Column()
    private Long updated_datetime;

    public boolean save(Connection connection) {
        return Boolean.TRUE;
    }

}
