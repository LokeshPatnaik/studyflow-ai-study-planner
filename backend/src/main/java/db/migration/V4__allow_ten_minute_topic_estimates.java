package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Loosens the topic estimate check without changing any stored estimates. */
public class V4__allow_ten_minute_topic_estimates extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String product = connection.getMetaData().getDatabaseProductName().toLowerCase();
        List<String> constraints;
        if (product.contains("postgresql")) {
            constraints = postgresEstimateConstraints(connection);
        } else if (product.contains("h2")) {
            constraints = h2EstimateConstraints(connection);
        } else {
            throw new IllegalStateException("V4 topic estimate migration does not support database: " + product);
        }

        try (Statement statement = connection.createStatement()) {
            for (String constraint : constraints) {
                statement.execute("ALTER TABLE syllabus_topics DROP CONSTRAINT \""
                        + constraint.replace("\"", "\"\"") + "\"");
            }
            statement.execute("ALTER TABLE syllabus_topics ADD CONSTRAINT ck_topic_estimated_minutes "
                    + "CHECK (estimated_minutes BETWEEN 10 AND 720)");
        }
    }

    private List<String> postgresEstimateConstraints(Connection connection) throws Exception {
        String sql = "SELECT conname FROM pg_constraint WHERE conrelid = to_regclass('syllabus_topics') "
                + "AND contype = 'c' AND pg_get_constraintdef(oid) ILIKE '%estimated_minutes%'";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            return readNames(results);
        }
    }

    private List<String> h2EstimateConstraints(Connection connection) throws Exception {
        String sql = "SELECT tc.CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc "
                + "JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc ON "
                + "tc.CONSTRAINT_CATALOG = cc.CONSTRAINT_CATALOG AND "
                + "tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA AND "
                + "tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME "
                + "WHERE UPPER(tc.TABLE_NAME) = 'SYLLABUS_TOPICS' AND tc.CONSTRAINT_TYPE = 'CHECK' "
                + "AND UPPER(cc.CHECK_CLAUSE) LIKE '%ESTIMATED_MINUTES%'";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            return readNames(results);
        }
    }

    private List<String> readNames(ResultSet results) throws Exception {
        List<String> names = new ArrayList<>();
        while (results.next()) names.add(results.getString(1));
        return names;
    }
}
