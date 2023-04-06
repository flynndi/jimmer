package org.babyfish.jimmer.sql.microservice;

import org.babyfish.jimmer.ImmutableObjects;
import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.runtime.ImmutableSpi;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.mutation.AbstractEntitySaveCommand;
import org.babyfish.jimmer.sql.ast.tuple.Tuple2;
import org.babyfish.jimmer.sql.common.AbstractMutationTest;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.babyfish.jimmer.sql.model.JimmerModule;
import org.babyfish.jimmer.sql.model.microservice.Order;
import org.babyfish.jimmer.sql.model.microservice.OrderDraft;
import org.babyfish.jimmer.sql.model.microservice.OrderItemDraft;
import org.babyfish.jimmer.sql.model.microservice.ProductDraft;
import org.babyfish.jimmer.sql.runtime.ConnectionManager;
import org.babyfish.jimmer.sql.runtime.MicroServiceExchange;
import org.babyfish.jimmer.sql.runtime.MicroServiceExporter;
import org.babyfish.jimmer.sql.runtime.SaveException;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public class MicroServiceMutationTest extends AbstractMutationTest {

    @Test
    public void testSaveManyToOneWithId() {
        executeAndExpectResult(
                getSqlClient(cfg -> {
                    cfg
                            .setMicroServiceName("order-item-service")
                            .setMicroServiceExchange(new MicroServiceExchangeImpl());
                }).getEntities().saveCommand(
                        OrderItemDraft.$.produce(item -> {
                            item.setId(100L);
                            item.setName("new-item");
                            item.setOrder(ImmutableObjects.makeIdOnly(Order.class, 1L));
                        })
                ).configure(AbstractEntitySaveCommand.Cfg::setAutoIdOnlyTargetCheckingAll),
                ctx -> {
                    ctx.statement(it -> {
                        it.sql(
                                "select tb_1_.ID from MS_ORDER_ITEM as tb_1_ " +
                                        "where tb_1_.ID = ?"
                        );
                    });
                    ctx.statement(it -> {
                        it.sql(
                                "insert into MS_ORDER_ITEM(ID, NAME, ORDER_ID) values(?, ?, ?)"
                        );
                    });
                    ctx.entity(it -> {
                        it.original("{\"id\":100,\"name\":\"new-item\",\"order\":{\"id\":1}}");
                        it.modified("{\"id\":100,\"name\":\"new-item\",\"order\":{\"id\":1}}");
                    });
                }
        );
    }

    @Test
    public void testSaveManyToOneWithIllegalId() {
        executeAndExpectResult(
                getSqlClient(cfg -> {
                    cfg
                            .setMicroServiceName("order-item-service")
                            .setMicroServiceExchange(new MicroServiceExchangeImpl());
                }).getEntities().saveCommand(
                        OrderItemDraft.$.produce(item -> {
                            item.setId(100L);
                            item.setName("new-item");
                            item.setOrder(ImmutableObjects.makeIdOnly(Order.class, 10L));
                        })
                ).configure(AbstractEntitySaveCommand.Cfg::setAutoIdOnlyTargetCheckingAll),
                ctx -> {
                    ctx.throwable(it -> {
                        it.type(SaveException.class);
                        it.message("Save error caused by the path: \"<root>.order\": Illegal ids: [10]");
                    });
                }
        );
    }

    @Test
    public void testSaveManyToOneWithNonIdValue() {
        executeAndExpectResult(
                getSqlClient(cfg -> {
                    cfg
                            .setMicroServiceName("order-item-service")
                            .setMicroServiceExchange(new MicroServiceExchangeImpl());
                }).getEntities().saveCommand(
                        OrderItemDraft.$.produce(item -> {
                            item.setId(100L);
                            item.setName("new-item");
                            item.applyOrder(order -> order.setName("order"));
                        })
                ).configure(AbstractEntitySaveCommand.Cfg::setAutoIdOnlyTargetCheckingAll),
                ctx -> {
                    ctx.throwable(it -> {
                        it.type(SaveException.class);
                        it.message(
                                "Save error caused by the path: \"<root>\": " +
                                        "The property \"org.babyfish.jimmer.sql.model.microservice.OrderItem.order\" " +
                                        "is remote(across different microservices) association, " +
                                        "but it has associated object which is not id-only"
                        );
                    });
                }
        );
    }

    @Test
    public void testSaveOneToMany() {
        executeAndExpectResult(
                getSqlClient(cfg -> {
                    cfg
                            .setMicroServiceName("order-service")
                            .setMicroServiceExchange(new MicroServiceExchangeImpl());
                }).getEntities().saveCommand(
                        OrderDraft.$.produce(order -> {
                            order.setId(100L);
                            order.setName("new-order");
                            order.addIntoOrderItems(item -> item.setId(1L));
                        })
                ).configure(AbstractEntitySaveCommand.Cfg::setAutoIdOnlyTargetCheckingAll),
                ctx -> {
                    ctx.statement(it -> {});
                    ctx.statement(it -> {});
                    ctx.throwable(it -> {
                        it.type(SaveException.class);
                        it.message(
                                "Save error caused by the path: \"<root>\": " +
                                        "The property \"org.babyfish.jimmer.sql.model.microservice.Order.orderItems\" " +
                                        "which is reversed(with `mappedBy`) remote(across different microservices) association " +
                                        "cannot be supported by save command"
                        );
                    });
                }
        );
    }

    @Test
    public void testSaveManyToManyWithId() {
        executeAndExpectResult(
                getSqlClient(cfg -> {
                    cfg
                            .setMicroServiceName("order-item-service")
                            .setMicroServiceExchange(new MicroServiceExchangeImpl());
                }).getEntities().saveCommand(
                        OrderItemDraft.$.produce(item -> {
                            item.setId(100L);
                            item.setName("new-item");
                            item.applyOrder(order -> order.setId(1L));
                            item.addIntoProducts(product -> product.setId(1L));
                            item.addIntoProducts(product -> product.setId(3L));
                        })
                ).configure(AbstractEntitySaveCommand.Cfg::setAutoIdOnlyTargetCheckingAll),
                ctx -> {
                    ctx.statement(it -> {
                        it.sql(
                                "select tb_1_.ID " +
                                        "from MS_ORDER_ITEM as tb_1_ " +
                                        "where tb_1_.ID = ?"
                        );
                    });
                    ctx.statement(it -> {
                        it.sql(
                                "insert into MS_ORDER_ITEM(ID, NAME, ORDER_ID) values(?, ?, ?)"
                        );
                    });
                    ctx.statement(it -> {
                        it.sql(
                                "insert into MS_ORDER_ITEM_PRODUCT_MAPPING(ORDER_ITEM_ID, PRODUCT_ID) " +
                                        "values (?, ?), (?, ?)"
                        );
                    });
                    ctx.entity(it -> {
                        it.original(
                                "{" +
                                        "--->\"id\":100," +
                                        "--->\"name\":\"new-item\"," +
                                        "--->\"order\":{\"id\":1}," +
                                        "--->\"products\":[{\"id\":1},{\"id\":3}]" +
                                        "}"
                        );
                        it.modified(
                                "{" +
                                        "--->\"id\":100," +
                                        "--->\"name\":\"new-item\"," +
                                        "--->\"order\":{\"id\":1}," +
                                        "--->\"products\":[{\"id\":1},{\"id\":3}]" +
                                        "}"
                        );
                    });
                }
        );
    }

    @Test
    public void testSaveManyToManyWithIllegalId() {
        executeAndExpectResult(
                getSqlClient(cfg -> {
                    cfg
                            .setMicroServiceName("order-item-service")
                            .setMicroServiceExchange(new MicroServiceExchangeImpl());
                }).getEntities().saveCommand(
                        OrderItemDraft.$.produce(item -> {
                            item.setId(100L);
                            item.setName("new-item");
                            item.applyOrder(order -> order.setId(1L));
                            item.addIntoProducts(product -> product.setId(1L));
                            item.addIntoProducts(product -> product.setId(3L));
                            item.addIntoProducts(product -> product.setId(4L));
                            item.addIntoProducts(product -> product.setId(5L));
                        })
                ).configure(AbstractEntitySaveCommand.Cfg::setAutoIdOnlyTargetCheckingAll),
                ctx -> {
                    ctx.statement(it -> {});
                    ctx.statement(it -> {});
                    ctx.throwable(it -> {
                        it.type(SaveException.class);
                        it.message(
                                "Save error caused by the path: \"<root>.products\": Illegal ids: [4, 5]"
                        );
                    });
                }
        );
    }

    @Test
    public void testSaveManyToManyWithNonIdValue() {
        executeAndExpectResult(
                getSqlClient(cfg -> {
                    cfg
                            .setMicroServiceName("order-item-service")
                            .setMicroServiceExchange(new MicroServiceExchangeImpl());
                }).getEntities().saveCommand(
                        OrderItemDraft.$.produce(item -> {
                            item.setId(100L);
                            item.setName("new-item");
                            item.applyOrder(order -> order.setId(1L));
                            item.addIntoProducts(product -> product.setName("a"));
                            item.addIntoProducts(product -> product.setName("b"));
                        })
                ).configure(AbstractEntitySaveCommand.Cfg::setAutoIdOnlyTargetCheckingAll),
                ctx -> {
                    ctx.statement(it -> {});
                    ctx.statement(it -> {});
                    ctx.throwable(it -> {
                        it.type(SaveException.class);
                        it.message(
                                "Save error caused by the path: \"<root>\": " +
                                        "The property \"org.babyfish.jimmer.sql.model.microservice.OrderItem.products\" " +
                                        "is remote(across different microservices) association, " +
                                        "but it has associated object which is not id-only"
                        );
                    });
                }
        );
    }

    @Test
    public void saveReversedManyToMany() {
        executeAndExpectResult(
                getSqlClient(cfg -> {
                    cfg
                            .setMicroServiceName("product-service")
                            .setMicroServiceExchange(new MicroServiceExchangeImpl());
                }).getEntities().saveCommand(
                        ProductDraft.$.produce(product -> {
                            product.setId(1L);
                            product.setName("Mac M1");
                            product.addIntoOrderItems(item -> {
                                item.setId(1L);
                            });
                        })
                ),
                ctx -> {
                    ctx.statement(it -> {});
                    ctx.statement(it -> {});
                    ctx.throwable(it -> {
                        it.type(SaveException.class);
                        it.message(
                                "Save error caused by the path: \"<root>\": " +
                                        "The property \"org.babyfish.jimmer.sql.model.microservice.Product.orderItems\" " +
                                        "which is reversed(with `mappedBy`) remote(across different microservices) association " +
                                        "cannot be supported by save command"
                        );
                    });
                }
        );
    }

    private static class MicroServiceExchangeImpl implements MicroServiceExchange {

        private static final ConnectionManager CONNECTION_MANAGER =
                new ConnectionManager() {
                    @Override
                    public <R> R execute(Function<Connection, R> block) {
                        R[] ref = (R[])new Object[1];
                        jdbc(con -> {
                            ref[0] = block.apply(con);
                        });
                        return ref[0];
                    }
                };

        private final JSqlClient orderClient =
                JSqlClient
                        .newBuilder()
                        .setEntityManager(JimmerModule.ENTITY_MANAGER)
                        .setConnectionManager(CONNECTION_MANAGER)
                        .setMicroServiceName("order-service")
                        .setMicroServiceExchange(this)
                        .build();

        private final JSqlClient orderItemClient =
                JSqlClient
                        .newBuilder()
                        .setEntityManager(JimmerModule.ENTITY_MANAGER)
                        .setConnectionManager(CONNECTION_MANAGER)
                        .setMicroServiceName("order-item-service")
                        .setMicroServiceExchange(this)
                        .build();

        private final JSqlClient productClient =
                JSqlClient
                        .newBuilder()
                        .setEntityManager(JimmerModule.ENTITY_MANAGER)
                        .setConnectionManager(CONNECTION_MANAGER)
                        .setMicroServiceName("product-service")
                        .setMicroServiceExchange(this)
                        .build();

        @SuppressWarnings("unchecked")
        @Override
        public List<ImmutableSpi> findByIds(
                String microServiceName,
                Collection<?> ids,
                Fetcher<?> fetcher
        ) {
            return new MicroServiceExporter(sqlClient(microServiceName))
                    .findByIds(ids, fetcher);
        }

        @SuppressWarnings("unchecked")
        @Override
        public List<Tuple2<Object, ImmutableSpi>> findByAssociatedIds(
                String microServiceName,
                ImmutableProp prop,
                Collection<?> targetIds,
                Fetcher<?> fetcher
        ) {
            return new MicroServiceExporter(sqlClient(microServiceName))
                    .findByAssociatedIds(prop, targetIds, fetcher);
        }

        private JSqlClient sqlClient(String microServiceName) {
            switch (microServiceName) {
                case "order-service":
                    return orderClient;
                case "order-item-service":
                    return orderItemClient;
                case "product-service":
                    return productClient;
                default:
                    throw new IllegalArgumentException(
                            "Illegal microservice name \"" +
                                    microServiceName +
                                    "\""
                    );
            }
        }
    }
}
