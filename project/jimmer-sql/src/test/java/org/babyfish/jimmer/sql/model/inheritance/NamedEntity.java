package org.babyfish.jimmer.sql.model.inheritance;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.babyfish.jimmer.sql.*;

import java.time.LocalDateTime;

@MappedSuperclass
public interface NamedEntity {

    @Key
    String getName();

    @LogicalDeleted(value = "true")
    boolean getDeleted();

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime getCreatedTime();

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime getModifiedTime();
}
