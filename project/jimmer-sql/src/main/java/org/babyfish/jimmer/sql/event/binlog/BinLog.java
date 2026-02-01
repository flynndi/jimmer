package org.babyfish.jimmer.sql.event.binlog;

import org.babyfish.jimmer.jackson.codec.Node;

public interface BinLog {

    void accept(String tableName, Node oldData, Node newData);

    void accept(String tableName, Node oldData, Node newData, String reason);
}
