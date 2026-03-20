package com.test.engine.duckdb;

import com.jsql.model.accessible.DataAccess;
import com.test.AbstractTestSuite;
import org.hibernate.cfg.JdbcSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import spring.SpringApp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ConcreteDuckdbSuiteIT extends AbstractTestSuite {

    public ConcreteDuckdbSuiteIT() {
        var property = SpringApp.get("duckdb");
        this.jdbcURL = property.getProperty(JdbcSettings.JAKARTA_JDBC_URL);
        this.jdbcUser = property.getProperty(JdbcSettings.JAKARTA_JDBC_USER);
        this.jdbcPass = property.getProperty(JdbcSettings.JAKARTA_JDBC_PASSWORD);

        this.databaseToInject = "main";
        this.tableToInject = "items";
        this.columnToInject = "count";

        this.queryAssertDatabases = "SELECT table_schema FROM information_schema.tables";
        this.queryAssertTables = String.format("""
            select TABLE_NAME
            from INFORMATION_SCHEMA.tables
            where TABLE_SCHEMA='%s'
        """, this.databaseToInject);
        this.queryAssertColumns = String.format("""
            select COLUMN_NAME
            from information_schema.columns
            where TABLE_SCHEMA='%s'
            and TABLE_NAME='%s'
        """, this.databaseToInject, this.tableToInject);
        this.queryAssertValues = String.format("select %s from %s.%s", this.columnToInject, this.databaseToInject, this.tableToInject);
    }

    @AfterEach
    public void checkEngine() {
        Assertions.assertEquals(
            this.injectionModel.getMediatorEngine().getDuckdb(),
            this.injectionModel.getMediatorEngine().getEngine()
        );
    }
}
