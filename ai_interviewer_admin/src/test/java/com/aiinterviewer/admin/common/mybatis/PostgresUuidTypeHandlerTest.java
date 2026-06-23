package com.aiinterviewer.admin.common.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.UUID;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.junit.jupiter.api.Test;

class PostgresUuidTypeHandlerTest {

    private final PostgresUuidTypeHandler handler = new PostgresUuidTypeHandler();

    @Test
    void declaresUuidAndPostgresOtherMappingsForMybatisScanning() {
        MappedTypes mappedTypes = PostgresUuidTypeHandler.class.getAnnotation(MappedTypes.class);
        MappedJdbcTypes mappedJdbcTypes = PostgresUuidTypeHandler.class.getAnnotation(MappedJdbcTypes.class);

        assertThat(mappedTypes).isNotNull();
        assertThat(mappedTypes.value()).containsExactly(UUID.class);
        assertThat(mappedJdbcTypes).isNotNull();
        assertThat(mappedJdbcTypes.value()).containsExactly(JdbcType.OTHER);
    }

    @Test
    void bindsUuidAsPostgresOtherObject() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        UUID value = UUID.fromString("00000000-0000-0000-0000-000000000901");

        handler.setNonNullParameter(statement, 2, value, JdbcType.OTHER);

        verify(statement).setObject(2, value, Types.OTHER);
    }

    @Test
    void readsUuidObjectsStringsAndNulls() throws Exception {
        UUID value = UUID.fromString("00000000-0000-0000-0000-000000000902");
        ResultSet resultSet = mock(ResultSet.class);
        CallableStatement callableStatement = mock(CallableStatement.class);
        when(resultSet.getObject("id")).thenReturn(value);
        when(resultSet.getObject(2)).thenReturn(value.toString());
        when(callableStatement.getObject(3)).thenReturn(null);

        assertThat(handler.getNullableResult(resultSet, "id")).isEqualTo(value);
        assertThat(handler.getNullableResult(resultSet, 2)).isEqualTo(value);
        assertThat(handler.getNullableResult(callableStatement, 3)).isNull();
    }
}
