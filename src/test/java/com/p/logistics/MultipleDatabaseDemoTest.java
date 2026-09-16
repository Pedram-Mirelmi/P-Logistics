package com.p.logistics;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

class MultipleDatabaseDemoTest {

    @Test
    void connectToTwoDatabases() throws Exception {

        String logisticsUrl =
                "jdbc:postgresql://localhost:5433/logistics";

        String supplierUrl =
                "jdbc:postgresql://localhost:5434/supplier";

        try (
                Connection logisticsConnection =
                        DriverManager.getConnection(
                                logisticsUrl,
                                "logistics",
                                "logistics"
                        );

                Connection supplierConnection =
                        DriverManager.getConnection(
                                supplierUrl,
                                "supplier",
                                "supplier"
                        )
        ) {

            printDatabaseInfo("LOGISTICS", logisticsConnection);
            printDatabaseInfo("SUPPLIER", supplierConnection);
        }
    }

    private void printDatabaseInfo(
            String name,
            Connection connection
    ) throws Exception {

        try (
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT current_database(), current_user"
                )
        ) {
            rs.next();

            System.out.println(
                    name +
                            " -> database=" + rs.getString(1) +
                            ", user=" + rs.getString(2)
            );
        }
    }
}