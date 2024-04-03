package org.example.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.EntityManager;
import org.example.annotations.Column;

import java.sql.Connection;
import java.sql.ResultSet;

@NoArgsConstructor
@Data
public class BaseEntity {



    public BaseEntity(Connection connection) {

    }

    public boolean save(Connection c) throws IllegalAccessException {
        return EntityManager.getOrCreate().save(this,c);
    }

}
