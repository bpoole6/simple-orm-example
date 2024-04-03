package org.example;

import lombok.Data;

import java.lang.reflect.Field;

@Data
    public  class ColumnInfo {
        private String name;
        private boolean onConflict;
        private boolean updatable;
        private boolean insertable;
        private Field field;

    }
