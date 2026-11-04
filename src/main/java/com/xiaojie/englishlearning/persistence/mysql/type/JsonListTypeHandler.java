package com.xiaojie.englishlearning.persistence.mysql.type;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public abstract class JsonListTypeHandler<T> extends BaseTypeHandler<List<T>> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    protected abstract Class<T> elementType();

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, List<T> value, JdbcType jdbcType)
            throws SQLException {
        try {
            statement.setString(index, MAPPER.writeValueAsString(value));
        } catch (IOException ex) {
            throw new SQLException("Unable to serialize JSON list", ex);
        }
    }

    @Override
    public List<T> getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return parse(resultSet.getString(columnName));
    }

    @Override
    public List<T> getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return parse(resultSet.getString(columnIndex));
    }

    @Override
    public List<T> getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return parse(statement.getString(columnIndex));
    }

    private List<T> parse(String value) throws SQLException {
        if (value == null || value.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(value, MAPPER.getTypeFactory().constructCollectionType(List.class, elementType()));
        } catch (IOException ex) {
            throw new SQLException("Unable to deserialize JSON list", ex);
        }
    }
}
