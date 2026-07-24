package com.aiinterviewer.job.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobSkillsJsonbTypeHandlerTest {

    private final JobSkillsJsonbTypeHandler handler = new JobSkillsJsonbTypeHandler();

    @Test
    void bindsSkillsAsPostgresJsonb() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);

        handler.setNonNullParameter(statement, 3, List.of("Java", "Docker"), null);

        verify(statement).setObject(3, "[\"Java\",\"Docker\"]", Types.OTHER);
    }

    @Test
    void readsSkillsFromJsonbText() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("skills")).thenReturn("[\"Java\",\"PostgreSQL\"]");

        assertThat(handler.getNullableResult(resultSet, "skills"))
                .containsExactly("Java", "PostgreSQL");
    }
}
