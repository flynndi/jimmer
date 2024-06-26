package org.babyfish.jimmer.sql.query;

import org.babyfish.jimmer.sql.ast.query.Order;
import org.babyfish.jimmer.sql.common.AbstractQueryTest;
import org.babyfish.jimmer.sql.model.embedded.*;
import org.babyfish.jimmer.sql.model.embedded.dto.*;
import org.junit.jupiter.api.Test;

public class EmbeddedQueryTest extends AbstractQueryTest {

    @Test
    public void testEmbeddedProp() {
        TransformTable table = TransformTable.$;
        executeAndExpect(
                getSqlClient()
                        .createQuery(table)
                        .orderBy(
                                Order.makeOrders(
                                        table,
                                        "source.leftTop.x asc, target.rightBottom.y desc"
                                )
                        )
                        .select(table),
                ctx -> {
                    ctx.sql(
                            "select " +
                                    "tb_1_.ID, " +
                                    "tb_1_.`LEFT`, tb_1_.TOP, tb_1_.`RIGHT`, tb_1_.BOTTOM, " +
                                    "tb_1_.TARGET_LEFT, tb_1_.TARGET_TOP, tb_1_.TARGET_RIGHT, tb_1_.TARGET_BOTTOM " +
                                    "from TRANSFORM tb_1_ " +
                                    "order by tb_1_.`LEFT` asc, tb_1_.TARGET_BOTTOM desc"
                    );
                }
        );
    }

    @Test
    public void testObjectFetcher() {
        TransformTable table = TransformTable.$;
        executeAndExpect(
                getSqlClient()
                        .createQuery(table)
                        .select(
                                table.fetch(
                                        TransformFetcher.$
                                                .source(
                                                        RectFetcher.$
                                                                .leftTop(
                                                                        PointFetcher.$
                                                                                .x()
                                                                )
                                                )
                                                .target(
                                                        RectFetcher.$
                                                                .rightBottom(
                                                                        PointFetcher.$
                                                                                .y()
                                                                )
                                                )
                                )
                        ),
                ctx -> {
                    ctx.sql(
                            "select tb_1_.ID, tb_1_.`LEFT`, tb_1_.TARGET_BOTTOM " +
                                    "from TRANSFORM tb_1_"
                    );
                    ctx.rows(
                            "[" +
                                    "--->{" +
                                    "--->--->\"id\":1," +
                                    "--->--->\"source\":{" +
                                    "--->--->--->\"leftTop\":{\"x\":100}" +
                                    "--->--->}," +
                                    "--->--->\"target\":{" +
                                    "--->--->--->\"rightBottom\":{\"y\":1000}" +
                                    "--->--->}" +
                                    "--->},{" +
                                    "--->--->\"id\":2," +
                                    "--->--->\"source\":{" +
                                    "--->--->--->\"leftTop\":{\"x\":150}" +
                                    "--->--->}," +
                                    "--->--->\"target\":null" +
                                    "--->}" +
                                    "]"
                    );
                }
        );
    }

    @Test
    public void testDto() {
        TransformTable table = TransformTable.$;
        executeAndExpect(
                getSqlClient()
                        .createQuery(table)
                        .select(table.fetch(TransformView.class)),
                ctx -> {
                    ctx.sql(
                            "select tb_1_.ID, tb_1_.`LEFT`, tb_1_.TARGET_BOTTOM from TRANSFORM tb_1_"
                    );
                    ctx.rows(rows -> {
                        assertContentEquals(
                                "[" +
                                        "--->TransformView(" +
                                        "--->--->id=1, " +
                                        "--->--->source=TransformView.TargetOf_source(" +
                                        "--->--->--->leftTop=TransformView.TargetOf_source.TargetOf_leftTop(" +
                                        "--->--->--->--->x=100" +
                                        "--->--->--->)" +
                                        "--->--->), " +
                                        "--->--->target=TransformView.TargetOf_target(" +
                                        "--->--->--->rightBottom=TransformView.TargetOf_target.TargetOf_rightBottom(" +
                                        "--->--->--->--->y=1000" +
                                        "--->--->--->)" +
                                        "--->--->)" +
                                        "--->), TransformView(" +
                                        "--->--->--->id=2, " +
                                        "--->--->--->source=TransformView.TargetOf_source(" +
                                        "--->--->--->--->leftTop=TransformView.TargetOf_source.TargetOf_leftTop(" +
                                        "--->--->--->--->x=150" +
                                        "--->--->--->)" +
                                        "--->--->), " +
                                        "--->--->target=null" +
                                        "--->)" +
                                        "]",
                                rows
                        );
                    });
                }
        );
    }

    @Test
    public void testDtoWithFormula() {
        TransformTable table = TransformTable.$;
        executeAndExpect(
                getSqlClient()
                        .createQuery(table)
                        .select(table.fetch(TransformView2.class)),
                ctx -> {
                    ctx.sql(
                            "select tb_1_.ID, " +
                                    "tb_1_.`LEFT`, tb_1_.TOP, tb_1_.`RIGHT`, tb_1_.BOTTOM, " +
                                    "tb_1_.TARGET_RIGHT, tb_1_.TARGET_BOTTOM, tb_1_.TARGET_LEFT, tb_1_.TARGET_TOP " +
                                    "from TRANSFORM tb_1_"
                    );
                    ctx.rows(it -> {
                        assertContentEquals(
                                "[" +
                                        "--->TransformView2(" +
                                        "--->--->id=1, " +
                                        "--->--->source=TransformView2.TargetOf_source(" +
                                        "--->--->--->area=60000.0, " +
                                        "--->--->--->leftTop=TransformView2.TargetOf_source.TargetOf_leftTop(x=100)" +
                                        "--->--->), " +
                                        "--->--->target=TransformView2.TargetOf_target(" +
                                        "--->--->--->area=240000.0, " +
                                        "--->--->--->rightBottom=TransformView2.TargetOf_target.TargetOf_rightBottom(y=1000)" +
                                        "--->--->)" +
                                        "--->), " +
                                        "--->TransformView2(" +
                                        "--->--->id=2, " +
                                        "--->--->source=TransformView2.TargetOf_source(" +
                                        "--->--->--->area=60000.0, " +
                                        "--->--->--->leftTop=TransformView2.TargetOf_source.TargetOf_leftTop(x=150)" +
                                        "--->--->), " +
                                        "--->--->target=null" +
                                        "--->)" +
                                        "]",
                                it
                        );
                    });
                }
        );
    }

    @Test
    public void testFlatDto() {
        TransformTable table = TransformTable.$;
        executeAndExpect(
                getSqlClient()
                        .createQuery(table)
                        .select(
                                table.fetch(TransformFlatView.class)
                        ),
                ctx -> {
                    ctx.sql(
                            "select tb_1_.ID, " +
                                    "tb_1_.`LEFT`, tb_1_.TOP, tb_1_.`RIGHT`, tb_1_.BOTTOM, " +
                                    "tb_1_.TARGET_LEFT, tb_1_.TARGET_TOP, tb_1_.TARGET_RIGHT, tb_1_.TARGET_BOTTOM " +
                                    "from TRANSFORM tb_1_"
                    );
                    ctx.rows(it -> {
                        assertContentEquals(
                                "[" +
                                        "--->TransformFlatView(" +
                                        "--->--->id=1, " +
                                        "--->--->sourceX1=100, " +
                                        "--->--->sourceY1=120, " +
                                        "--->--->sourceX2=400, " +
                                        "--->--->sourceY2=320, " +
                                        "--->--->targetX1=800, " +
                                        "--->--->targetY1=600, " +
                                        "--->--->targetX2=1400, " +
                                        "--->--->targetY2=1000" +
                                        "--->), " +
                                        "--->TransformFlatView(" +
                                        "--->--->id=2, " +
                                        "--->--->sourceX1=150, " +
                                        "--->--->sourceY1=170, " +
                                        "--->--->sourceX2=450, " +
                                        "--->--->sourceY2=370, " +
                                        "--->--->targetX1=null, " +
                                        "--->--->targetY1=null, " +
                                        "--->--->targetX2=null, " +
                                        "--->--->targetY2=null" +
                                        "--->)" +
                                        "]",
                                it
                        );
                    });
                }
        );
    }

    @Test
    public void testEmbeddedJson() {
        MachineTable table = MachineTable.$;
        executeAndExpect(
                getSqlClient()
                        .createQuery(table)
                        .select(
                                table.fetch(
                                        MachineFetcher.$
                                                .detail(
                                                        MachineDetailFetcher.$
                                                                .factories()
                                                )
                                )
                        ),
                ctx -> {
                    ctx.sql(
                            "select tb_1_.ID, tb_1_.factory_map from MACHINE tb_1_"
                    );
                    ctx.rows(
                            "[{\"id\":1,\"detail\":{\"factories\":{\"f-1\":\"factory-1\",\"f-2\":\"factory-2\"}}}]"
                    );
                }
        );
    }

    @Test
    public void testFormulaBaseOnEmbedded() {
        MachineTable table = MachineTable.$;
        executeAndExpect(
                getSqlClient()
                        .createQuery(table)
                        .select(
                                table.fetch(
                                        MachineFetcher.$
                                                .factoryCount()
                                                .factoryNames()
                                                .detail(
                                                        MachineDetailFetcher.$
                                                                .patents()
                                                )
                                )
                        ),
                ctx -> {
                    ctx.sql("select tb_1_.ID, tb_1_.patent_map, tb_1_.factory_map from MACHINE tb_1_");
                    ctx.rows(
                            "[" +
                                    "--->{" +
                                    "--->--->\"id\":1," +
                                    "--->--->\"detail\":{" +
                                    "--->--->--->\"patents\":{\"p-1\":\"patent-1\",\"p-2\":\"patent-2\"}" +
                                    "--->--->}," +
                                    "--->--->\"factoryCount\":2," +
                                    "--->--->\"factoryNames\":[\"f-1\",\"f-2\"]" +
                                    "--->}" +
                                    "]"
                    );
                }
        );
    }

    @Test
    public void testFormulaBaseOnEmbeddedAndFetchEmbedded() {
        MachineTable table = MachineTable.$;
        executeAndExpect(
                getSqlClient()
                        .createQuery(table)
                        .select(
                                table.fetch(
                                        MachineFetcher.$
                                                .factoryCount()
                                                .detail(
                                                        MachineDetailFetcher.$
                                                                .patents()
                                                )
                                )
                        ),
                ctx -> {
                    ctx.sql(
                            "select tb_1_.ID, tb_1_.patent_map, tb_1_.factory_map from MACHINE tb_1_"
                    );
                    ctx.rows(
                            "[" +
                                    "--->{" +
                                    "--->--->\"id\":1," +
                                    "--->--->\"detail\":{\"patents\":{\"p-1\":\"patent-1\",\"p-2\":\"patent-2\"}}," +
                                    "--->--->\"factoryCount\":2" +
                                    "--->}" +
                                    "]"
                    );
                }
        );
    }

    @Test
    public void testFormulaBaseOnEmbeddedAndFetchDuplicatedEmbedded() {
        MachineTable table = MachineTable.$;
        executeAndExpect(
                getSqlClient()
                        .createQuery(table)
                        .select(
                                table.fetch(
                                        MachineFetcher.$
                                                .factoryCount()
                                                .detail(
                                                        MachineDetailFetcher.$
                                                                .patents()
                                                                .factories()
                                                )
                                )
                        ),
                ctx -> {
                    ctx.sql(
                            "select tb_1_.ID, tb_1_.patent_map, tb_1_.factory_map from MACHINE tb_1_"
                    );
                    ctx.rows(
                            "[" +
                                    "--->{" +
                                    "--->--->\"id\":1," +
                                    "--->--->\"detail\":{" +
                                    "--->--->--->\"factories\":{\"f-1\":\"factory-1\",\"f-2\":\"factory-2\"}," +
                                    "--->--->--->\"patents\":{\"p-1\":\"patent-1\",\"p-2\":\"patent-2\"}" +
                                    "--->--->}," +
                                    "--->--->\"factoryCount\":2" +
                                    "--->}" +
                                    "]"
                    );
                }
        );
    }

    @Test
    public void testFormulaInEmbeddable() {
        TransformTable table = TransformTable.$;
        executeAndExpect(
                getSqlClient()
                        .createQuery(table)
                        .select(
                                table.fetch(
                                        TransformFetcher.$
                                                .source(
                                                        RectFetcher.$.area()
                                                )
                                                .target(
                                                        RectFetcher.$.area()
                                                )
                                )
                        ),
                ctx -> {
                    ctx.sql(
                            "select tb_1_.ID, " +
                                    "tb_1_.`LEFT`, tb_1_.TOP, tb_1_.`RIGHT`, tb_1_.BOTTOM, " +
                                    "tb_1_.TARGET_LEFT, tb_1_.TARGET_TOP, tb_1_.TARGET_RIGHT, tb_1_.TARGET_BOTTOM " +
                                    "from TRANSFORM tb_1_"
                    );
                    ctx.rows(
                            "[" +
                                    "--->{" +
                                    "--->--->\"id\":1," +
                                    "--->--->\"source\":{\"area\":60000.0}," +
                                    "--->--->\"target\":{\"area\":240000.0}" +
                                    "--->},{" +
                                    "--->--->\"id\":2," +
                                    "--->--->\"source\":{\"area\":60000.0}," +
                                    "--->--->\"target\":null" +
                                    "--->}" +
                                    "]"
                    );
                }
        );
    }

    @Test
    public void testFormulaInEmbeddableAndDuplicatedFetch() {
        TransformTable table = TransformTable.$;
        executeAndExpect(
                getSqlClient()
                        .createQuery(table)
                        .select(
                                table.fetch(
                                        TransformFetcher.$
                                                .source(
                                                        RectFetcher.$
                                                                .area()
                                                                .leftTop(PointFetcher.$.x())
                                                )
                                                .target(
                                                        RectFetcher.$
                                                                .area()
                                                                .rightBottom(PointFetcher.$.y())
                                                )
                                )
                        ),
                ctx -> {
                    ctx.sql(
                            "select tb_1_.ID, " +
                                    "tb_1_.`LEFT`, tb_1_.TOP, tb_1_.`RIGHT`, tb_1_.BOTTOM, " +
                                    "tb_1_.TARGET_RIGHT, tb_1_.TARGET_BOTTOM, tb_1_.TARGET_LEFT, tb_1_.TARGET_TOP " +
                                    "from TRANSFORM tb_1_"
                    );
                    ctx.rows(
                            "[" +
                                    "--->{" +
                                    "--->--->\"id\":1," +
                                    "--->--->\"source\":{" +
                                    "--->--->--->\"leftTop\":{\"x\":100}," +
                                    "--->--->--->\"area\":60000.0" +
                                    "--->--->}," +
                                    "--->--->\"target\":{" +
                                    "--->--->--->\"rightBottom\":{\"y\":1000}," +
                                    "--->--->--->\"area\":240000.0" +
                                    "--->--->}" +
                                    "--->},{" +
                                    "--->--->\"id\":2," +
                                    "--->--->\"source\":{" +
                                    "--->--->--->\"leftTop\":{\"x\":150}," +
                                    "--->--->--->\"area\":60000.0" +
                                    "--->--->}," +
                                    "--->--->\"target\":null" +
                                    "--->}" +
                                    "]"
                    );
                }
        );
    }

    @Test
    public void testSpecification() {
        TransformTable table = TransformTable.$;
        TransformSpecification specification = new TransformSpecification();
        specification.setMinX(100L);
        specification.setMaxX(2000L);
        executeAndExpect(
                getSqlClient()
                        .createQuery(table)
                        .where(specification)
                        .select(table),
                ctx -> {
                    ctx.sql(
                            "select tb_1_.ID, " +
                                    "tb_1_.`LEFT`, tb_1_.TOP, tb_1_.`RIGHT`, tb_1_.BOTTOM, " +
                                    "tb_1_.TARGET_LEFT, tb_1_.TARGET_TOP, tb_1_.TARGET_RIGHT, tb_1_.TARGET_BOTTOM " +
                                    "from TRANSFORM tb_1_ " +
                                    "where tb_1_.`LEFT` >= ? and tb_1_.TARGET_RIGHT <= ?"
                    ).variables(100L, 2000L);
                    ctx.rows(
                            "[{" +
                                    "--->\"id\":1," +
                                    "--->\"source\":{" +
                                    "--->--->\"leftTop\":{\"x\":100,\"y\":120}," +
                                    "--->--->\"rightBottom\":{\"x\":400,\"y\":320}" +
                                    "--->}," +
                                    "--->\"target\":{" +
                                    "--->--->\"leftTop\":{\"x\":800,\"y\":600}," +
                                    "--->--->\"rightBottom\":{\"x\":1400,\"y\":1000}" +
                                    "--->}" +
                                    "}]"
                    );
                }
        );
    }

    @Test
    public void findByFlatSpecification() {
        MachineTable table = MachineTable.$;
        MachineSpecification specification = new MachineSpecification();
        specification.setHost("localhost");
        specification.setPort(8080);
        executeAndExpect(
                getSqlClient()
                        .createQuery(table)
                        .where(specification)
                        .select(table),
                ctx -> {
                    ctx.sql(
                            "select tb_1_.ID, tb_1_.HOST, tb_1_.PORT, tb_1_.SECONDARY_HOST, tb_1_.SECONDARY_PORT, " +
                                    "tb_1_.CPU_FREQUENCY, tb_1_.MEMORY_SIZE, tb_1_.DISK_SIZE, " +
                                    "tb_1_.factory_map, tb_1_.patent_map " +
                                    "from MACHINE " +
                                    "tb_1_ where tb_1_.HOST = ? and tb_1_.PORT = ?"
                    );
                    ctx.rows(
                            "[" +
                                    "--->{" +
                                    "--->--->\"id\":1," +
                                    "--->--->\"location\":{\"host\":\"localhost\",\"port\":8080}," +
                                    "--->--->\"secondaryLocation\":null," +
                                    "--->--->\"cpuFrequency\":2," +
                                    "--->--->\"memorySize\":8," +
                                    "--->--->\"diskSize\":256," +
                                    "--->--->\"detail\":{" +
                                    "--->--->--->\"factories\":{\"f-1\":\"factory-1\",\"f-2\":\"factory-2\"}," +
                                    "--->--->--->\"patents\":{\"p-1\":\"patent-1\",\"p-2\":\"patent-2\"}" +
                                    "--->--->}" +
                                    "--->}" +
                                    "]"
                    );
                }
        );
    }

    @Test
    public void findByNestedSpecification() {
        MachineTable table = MachineTable.$;
        MachineSpecification2 specification = new MachineSpecification2();
        MachineSpecification2.TargetOf_location location = new MachineSpecification2.TargetOf_location();
        location.setHost("localhost");
        location.setPort(8080);
        specification.setLocation(location);
        executeAndExpect(
                getSqlClient()
                        .createQuery(table)
                        .where(specification)
                        .select(table),
                ctx -> {
                    ctx.sql(
                            "select tb_1_.ID, tb_1_.HOST, tb_1_.PORT, tb_1_.SECONDARY_HOST, tb_1_.SECONDARY_PORT, " +
                                    "tb_1_.CPU_FREQUENCY, tb_1_.MEMORY_SIZE, tb_1_.DISK_SIZE, " +
                                    "tb_1_.factory_map, tb_1_.patent_map " +
                                    "from MACHINE " +
                                    "tb_1_ where tb_1_.HOST = ? and tb_1_.PORT = ?"
                    );
                    ctx.rows(
                            "[" +
                                    "--->{" +
                                    "--->--->\"id\":1," +
                                    "--->--->\"location\":{\"host\":\"localhost\",\"port\":8080}," +
                                    "--->--->\"secondaryLocation\":null," +
                                    "--->--->\"cpuFrequency\":2," +
                                    "--->--->\"memorySize\":8," +
                                    "--->--->\"diskSize\":256," +
                                    "--->--->\"detail\":{" +
                                    "--->--->--->\"factories\":{\"f-1\":\"factory-1\",\"f-2\":\"factory-2\"}," +
                                    "--->--->--->\"patents\":{\"p-1\":\"patent-1\",\"p-2\":\"patent-2\"}" +
                                    "--->--->}" +
                                    "--->}" +
                                    "]"
                    );
                }
        );
    }

    @Test
    public void testFetchNestedEmbeddedAsScalarOfIssue536() {
        connectAndExpect(
                con -> {
                    return getSqlClient()
                            .getEntities()
                            .forConnection(con)
                            .findById(
                                    TransformFetcher.$
                                            .sourceLeftTop(),
                                    1L
                            );
                }, ctx -> {
                    ctx.sql(
                            "select tb_1_.ID, tb_1_.`LEFT`, tb_1_.TOP " +
                                    "from TRANSFORM tb_1_ " +
                                    "where tb_1_.ID = ?"
                    );
                    ctx.rows(
                            "[{\"id\":1,\"sourceLeftTop\":{\"x\":100,\"y\":120}}]"
                    );
                });
    }

    @Test
    public void testDtoForIssue536() {
        connectAndExpect(
                con -> {
                    return getSqlClient()
                            .getEntities()
                            .forConnection(con)
                            .findById(
                                    TransformViewForIssue536.class,
                                    1L
                            );
                },
                ctx -> {
                    ctx.sql(
                            "select tb_1_.ID, tb_1_.`LEFT`, tb_1_.TOP " +
                                    "from TRANSFORM tb_1_ where tb_1_.ID = ?"
                    );
                    ctx.rows(rows -> {
                        assertContentEquals(
                                "TransformViewForIssue536(" +
                                        "--->id=1, " +
                                        "--->sourceLeftTop={\"x\":100,\"y\":120}" +
                                        ")",
                                rows.get(0)
                        );
                    });
                }
        );
    }

    @Test
    public void testFetcherOnEmbedded() {
        TransformTable table = TransformTable.$;
        executeAndExpect(
                getSqlClient()
                        .createQuery(table)
                        .select(
                                table.source().fetch(
                                        RectFetcher.$.leftTop()
                                )
                        ),
                ctx -> {
                    ctx.sql(
                            "select tb_1_.`LEFT`, tb_1_.TOP " +
                                    "from TRANSFORM tb_1_"
                    );
                    ctx.rows(
                            "[" +
                                    "--->{\"leftTop\":{\"x\":100,\"y\":120}}," +
                                    "--->{\"leftTop\":{\"x\":150,\"y\":170}}" +
                                    "]"
                    );
                }
        );
    }

    @Test
    public void testFormulaFetcherOnEmbedded() {
        TransformTable table = TransformTable.$;
        executeAndExpect(
                getSqlClient()
                        .createQuery(table)
                        .select(
                                table.target().rightBottom().fetch(
                                        PointFetcher.$.distance()
                                )
                        ),
                ctx -> {
                    ctx.sql(
                            "select tb_1_.TARGET_RIGHT, tb_1_.TARGET_BOTTOM " +
                                    "from TRANSFORM tb_1_"
                    );
                    ctx.rows(
                            "[" +
                                    "--->{\"distance\":1720.4650534085254}," +
                                    "--->null" +
                                    "]"
                    );
                }
        );
    }

    @Test
    public void testFetcherOnEmbeddedId() {
        OrderItemTable table = OrderItemTable.$;
        executeAndExpect(
                getSqlClient()
                        .createQuery(table)
                        .select(
                                table.order().id().fetch(
                                        OrderIdFetcher.$.x()
                                )
                        ),
                ctx -> {
                    ctx.sql(
                            "select tb_1_.FK_ORDER_X from ORDER_ITEM tb_1_"
                    );
                    ctx.rows(
                            "[" +
                                    "--->null," +
                                    "--->{\"x\":\"001\"}," +
                                    "--->{\"x\":\"001\"}," +
                                    "--->{\"x\":\"001\"}," +
                                    "--->{\"x\":\"001\"}" +
                                    "]"
                    );
                }
        );
    }
}
