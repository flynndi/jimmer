package org.babyfish.jimmer.sql.kt.query

import org.babyfish.jimmer.kt.new
import org.babyfish.jimmer.sql.ast.tuple.Tuple2
import org.babyfish.jimmer.sql.dialect.H2Dialect
import org.babyfish.jimmer.sql.kt.ast.expression.nullableValueIn
import org.babyfish.jimmer.sql.kt.ast.expression.nullableValueNotIn
import org.babyfish.jimmer.sql.kt.ast.expression.tuple
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.babyfish.jimmer.sql.kt.common.AbstractQueryTest
import org.babyfish.jimmer.sql.kt.model.TreeNode
import org.babyfish.jimmer.sql.kt.model.embedded.*
import org.babyfish.jimmer.sql.kt.model.embedded.p4bug524.Point
import org.babyfish.jimmer.sql.kt.model.name
import org.babyfish.jimmer.sql.kt.model.parentId
import org.junit.Test

class InCollectionTest : AbstractQueryTest() {

    @Test
    fun testTuple() {
        executeAndExpect(
            sqlClient {
                setDialect(object: H2Dialect() {
                    override fun getMaxInListSize(): Int = 5
                })
                setInListPaddingEnabled(true)
                setExpandedInListPaddingEnabled(true)
            }.createQuery(TreeNode::class) {
                where += tuple(
                    table.parentId,
                    table.name
                ) nullableValueIn listOf(
                    Tuple2(1L, "Food"),
                    Tuple2(1L, "Cloth"),
                    Tuple2(2L, "Drinks"),
                    Tuple2(2L, "Bread"),
                    Tuple2(null, "Home"),
                    Tuple2(3L, "Cococola"),
                    Tuple2(3L, "Fenta"),
                    Tuple2(4L, "Cococola"),
                    Tuple2(5L, "Fenta"),
                    Tuple2(null, "Cococola"),
                    Tuple2(null, "Fenta")
                )
                select(table)
            }
        ) {
            sql(
                """select tb_1_.NODE_ID, tb_1_.NAME, tb_1_.PARENT_ID 
                    |from TREE_NODE tb_1_ 
                    |where (
                    |--->(tb_1_.PARENT_ID, tb_1_.NAME) in ((?, ?), (?, ?), (?, ?), (?, ?), (?, ?)) 
                    |--->or 
                    |--->(tb_1_.PARENT_ID, tb_1_.NAME) in ((?, ?), (?, ?), (?, ?), (?, ?)) 
                    |--->or tb_1_.PARENT_ID is null 
                    |--->and tb_1_.NAME = any(?)
                    |)""".trimMargin()
            ).variables(
                1L, "Food", 1L, "Cloth", 2L, "Drinks", 2L, "Bread", 3L, "Cococola",
                3L, "Fenta", 4L, "Cococola", 5L, "Fenta",
                5L, "Fenta", // repeated data by `jimmer.in-list-padding-enabled`
                // repeated data by `jimmer.in-list-padding-enabled`
                arrayOf<Any>("Home", "Cococola", "Fenta")
            )
        }
    }

    @Test
    fun testEmbedded() {
        executeAndExpect(
            sqlClient.createQuery(Machine::class) {
                where += table.location nullableValueIn(
                    listOf(
                        new(Location::class).by {
                            host = "localhost"
                            port = 80
                        },
                        new(Location::class).by {
                            host = "localhost"
                            port = 443
                        },
                        new(Location::class).by {
                            host = "127.0.0.1"
                            port = 80
                        },
                        new(Location::class).by {
                            host = "127.0.0.1"
                            port = 443
                        },
                        new(Location::class).by {
                            host = "localhost"
                            port = null
                        },
                        new(Location::class).by {
                            host = "127.0.0.1"
                            port = null
                        }
                    )
                )
                select(
                    table.fetchBy {
                        location()
                    }
                )
            }
        ) {
            sql(
                """select tb_1_.ID, tb_1_.HOST, tb_1_.PORT 
                    |from MACHINE tb_1_ 
                    |where (
                    |--->(tb_1_.HOST, tb_1_.PORT) in ((?, ?), (?, ?), (?, ?), (?, ?)) 
                    |--->or 
                    |--->tb_1_.PORT is null and tb_1_.HOST in (?, ?)
                    |)""".trimMargin()
            ).variables(
                "localhost",
                80,
                "localhost",
                443,
                "127.0.0.1",
                80,
                "127.0.0.1",
                443,
                "localhost",
                "127.0.0.1"
            )
        }
    }

    @Test
    fun testEmbeddedPathOfBug596() {
        executeAndExpect(
            sqlClient.createQuery(Machine::class) {
                where(table.location.host valueIn listOf("localshot", "127.0.0.1"))
                select(table.id)
            }
        ) {
            sql(
                """select tb_1_.ID 
                    |from MACHINE tb_1_ 
                    |where tb_1_.HOST in (?, ?)""".trimMargin()
            )
        }
    }

    @Test
    fun testShallowEmbeddedPathOfBug596() {
        executeAndExpect(
            sqlClient
                .createQuery(Transform::class) {
                    where(
                        table.source.leftTop valueIn listOf(
                            Point {
                                x = 100
                                y = 120
                            },
                            Point {
                                x = 150
                                y = 170
                            }
                        )
                    )
                    select(table.id)
                }
        ) {
            sql(
                """select tb_1_.ID 
                    |from TRANSFORM tb_1_ 
                    |where (tb_1_.`LEFT`, tb_1_.TOP) in ((?, ?), (?, ?))""".trimMargin()
            )
        }
    }

    @Test
    fun testNotIn() {
        executeAndExpect(
            sqlClient.createQuery(Machine::class) {
                where += table.location nullableValueNotIn(
                    listOf(
                        new(Location::class).by {
                            host = "localhost"
                            port = 80
                        },
                        new(Location::class).by {
                            host = "localhost"
                            port = 443
                        },
                        new(Location::class).by {
                            host = "127.0.0.1"
                            port = 80
                        },
                        new(Location::class).by {
                            host = "127.0.0.1"
                            port = 443
                        },
                        new(Location::class).by {
                            host = "localhost"
                            port = null
                        },
                        new(Location::class).by {
                            host = "127.0.0.1"
                            port = null
                        }
                    )
                    )
                select(
                    table.fetchBy {
                        location()
                    }
                )
            }
        ) {
            sql(
                """select tb_1_.ID, tb_1_.HOST, tb_1_.PORT 
                    |from MACHINE tb_1_ 
                    |where 
                    |--->(tb_1_.HOST, tb_1_.PORT) not in (
                    |--->--->(?, ?), (?, ?), (?, ?), (?, ?)
                    |--->) and (
                    |--->--->tb_1_.PORT is not null or 
                    |--->--->tb_1_.HOST not in (?, ?)
                    |--->)""".trimMargin()
            ).variables(
                "localhost",
                80,
                "localhost",
                443,
                "127.0.0.1",
                80,
                "127.0.0.1",
                443,
                "localhost",
                "127.0.0.1"
            )
        }
    }
}