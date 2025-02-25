package org.babyfish.jimmer.sql.ast.impl;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.JoinType;
import org.babyfish.jimmer.sql.ast.Expression;
import org.babyfish.jimmer.sql.ast.Predicate;
import org.babyfish.jimmer.sql.ast.Selection;
import org.babyfish.jimmer.sql.ast.impl.associated.VirtualPredicateMergedResult;
import org.babyfish.jimmer.sql.ast.impl.table.*;
import org.babyfish.jimmer.sql.ast.impl.util.IdentityMap;
import org.babyfish.jimmer.sql.ast.impl.query.*;
import org.babyfish.jimmer.sql.ast.impl.util.*;
import org.babyfish.jimmer.sql.ast.query.*;
import org.babyfish.jimmer.sql.ast.table.AssociationTable;
import org.babyfish.jimmer.sql.ast.table.Props;
import org.babyfish.jimmer.sql.ast.table.Table;
import org.babyfish.jimmer.sql.ast.table.TableEx;
import org.babyfish.jimmer.sql.ast.table.spi.TableProxy;
import org.babyfish.jimmer.sql.filter.Filter;
import org.babyfish.jimmer.sql.filter.impl.FilterArgsImpl;
import org.babyfish.jimmer.sql.filter.impl.FilterManager;
import org.babyfish.jimmer.sql.runtime.ExecutionPurpose;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.checkerframework.checker.units.qual.A;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class AbstractMutableStatementImpl implements FilterableImplementor, MutableStatementImplementor {

    private static final Predicate[] EMPTY_PREDICATES = new Predicate[0];

    private final JSqlClientImplementor sqlClient;

    private final ImmutableType type;

    private List<Predicate> predicates = new ArrayList<>();

    private Table<?> table;

    private TableImplementor<?> tableImplementor;

    private boolean frozen;

    private int modCount;

    private final IdentityMap<TableImplementor<?>, List<Predicate>> filterPredicates = new IdentityMap<>();

    public AbstractMutableStatementImpl(
            JSqlClientImplementor sqlClient,
            ImmutableType type
    ) {
        if (!type.isEntity()) {
            throw new IllegalArgumentException("\"" + type + "\" is not entity");
        }
        if (sqlClient != null && !sqlClient.getMicroServiceName().equals(type.getMicroServiceName())) {
            throw new IllegalArgumentException(
                    "The sql client and entity type \"" +
                            type +
                            "\" do not belong to the same micro service: " +
                            "{sqlClient: \"" +
                            sqlClient.getMicroServiceName() +
                            "\", entity: \"" +
                            type.getMicroServiceName() +
                            "\"}"
            );
        }
        this.sqlClient = sqlClient;
        this.type = type;
    }

    public AbstractMutableStatementImpl(
            JSqlClientImplementor sqlClient,
            TableProxy<?> table
    ) {
        if (table.__unwrap() != null) {
            throw new IllegalArgumentException("table proxy cannot be wrapper");
        }
        if (table.__prop() != null) {
            throw new IllegalArgumentException("table proxy must be root table");
        }
        this.sqlClient = Objects.requireNonNull(
                sqlClient,
                "sqlClient cannot be null"
        );
        if (!sqlClient.getMicroServiceName().equals(table.getImmutableType().getMicroServiceName())) {
            throw new IllegalArgumentException(
                    "The sql client and entity type \"" +
                            table.getImmutableType() +
                            "\" do not belong to the same micro service: " +
                            "{sqlClient: \"" +
                            sqlClient.getMicroServiceName() +
                            "\", entity: \"" +
                            table.getImmutableType().getMicroServiceName() +
                            "\"}"
            );
        }
        this.table = table;
        this.type = table.getImmutableType();
    }

    @SuppressWarnings("unchecked")
    public <T extends Table<?>> T getTable() {
        Table<?> table = this.table;
        if (table == null) {
            this.table = table = TableProxies.wrap(getTableImplementor());
        }
        return (T)table;
    }

    public ImmutableType getType() {
        return type;
    }

    public TableImplementor<?> getTableImplementor() {
        TableImplementor<?> tableImplementor = this.tableImplementor;
        if (tableImplementor == null) {
            this.tableImplementor = tableImplementor =
                    TableImplementor.create(this, type);
        }
        return tableImplementor;
    }

    public List<Predicate> getPredicates() {
        return Collections.unmodifiableList(predicates);
    }

    protected List<Expression<?>> getGroupExpressions() {
        return Collections.emptyList();
    }

    protected List<Predicate> getHavingPredicates() {
        return Collections.emptyList();
    }

    protected void setHavingPredicates(List<Predicate> havingPredicates) {}

    protected List<Order> getOrders() {
        return Collections.emptyList();
    }

    public final Iterable<Predicate> unfrozenPredicates() {
        return new Iterable<Predicate>() {
            @NotNull
            @Override
            public Iterator<Predicate> iterator() {
                return ConcattedIterator.of(
                        predicates.iterator(),
                        new FlaternIterator<>(filterPredicates.iterator())
                );
            }
        };
    }

    public void whereByFilter(TableImplementor<?> tableImplementor, List<Predicate> predicates) {
        filterPredicates.put(tableImplementor, predicates);
    }

    public Predicate getPredicate(AstContext astContext) {
        freeze(astContext);
        List<Predicate> ps = predicates;
        return ps.isEmpty() ? null : ps.get(0);
    }

    public Predicate getFilterPredicate(TableImplementor<?> tableImplementor, AstContext astContext) {
        freeze(astContext);
        List<Predicate> ps = filterPredicates.get(tableImplementor);
        return ps == null || ps.isEmpty() ? null : ps.get(0);
    }

    public abstract StatementContext getContext();

    public abstract AbstractMutableStatementImpl getParent();

    @Override
    public MutableSubQuery createSubQuery(TableProxy<?> table) {
        return sqlClient.createSubQuery(table);
    }

    @Override
    public <SE, ST extends TableEx<SE>, TE, TT extends TableEx<TE>> MutableSubQuery createAssociationSubQuery(
            AssociationTable<SE, ST, TE, TT> table
    ) {
        return sqlClient.createAssociationSubQuery(table);
    }

    @Override
    public boolean hasVirtualPredicate() {
        for (Predicate predicate : unfrozenPredicates()) {
            if (((Ast)predicate).hasVirtualPredicate()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void resolveVirtualPredicate(AstContext ctx) {
        ctx.pushStatement(this);

        // Resolve real table implementation to get table alias immediately before resolving virtual predicates,
        // this is important because it makes table aliases of SQL looks beautiful
        getTableImplementor();

        predicates = ctx.resolveVirtualPredicates(predicates);
        List<Predicate> havingPredicates = getHavingPredicates();
        if (!havingPredicates.isEmpty()) {
            setHavingPredicates(ctx.resolveVirtualPredicates(havingPredicates));
        }
        filterPredicates.replaceAll(ctx::resolveVirtualPredicates);
        ctx.popStatement();
    }

    public final boolean freeze(AstContext ctx) {
        if (frozen) {
            return false;
        }
        onFrozen(ctx);
        frozen = true;
        return true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    protected void onFrozen(AstContext ctx) {
        filterPredicates.removeAll((t, ps) -> {
            if (ps.isEmpty()) {
                return true;
            }
            if (t.getParent() == null || t.getJoinType() == JoinType.INNER) {
                predicates.addAll(ps);
                return true;
            }
            return false;
        });
        predicates = mergePredicates(predicates);
        filterPredicates.replaceAll(AbstractMutableStatementImpl::mergePredicates);
    }

    public void applyVirtualPredicates(AstContext ctx) {
        int modCount = -1;
        while (modCount != ctx.modCount()) {
            modCount = ctx.modCount();
            resolveVirtualPredicate(ctx);
        }
    }

    public final void applyGlobalFilters(
            AstContext astContext,
            FilterLevel level,
            @Nullable List<Selection<?>> selections
    ) {
        if (level != FilterLevel.IGNORE_ALL) {
            applyGlobalFiltersImpl(new ApplyFilterVisitor(astContext, level), selections, null);
        }
    }

    public final void applyDataLoaderGlobalFilters(TableImplementor<?> table) {
        AstContext astContext = new AstContext(sqlClient);
        ApplyFilterVisitor visitor = new ApplyFilterVisitor(astContext, FilterLevel.DEFAULT);
        for (Predicate predicate : unfrozenPredicates()) {
            visitor.apply(this, predicate);
        }
        for (Order order : getOrders()) {
            visitor.apply(this, order);
        }
        getTableImplementor();
        applyGlobalFiltersImpl(visitor, null, table);
    }

    private void applyGlobalFiltersImpl(
            ApplyFilterVisitor visitor,
            List<Selection<?>> selections,
            TableImplementor<?> start
    ) {
        AstContext astContext = visitor.getAstContext();
        astContext.pushStatement(this);
        try {
            applyGlobalFilerImpl(visitor, start != null ? start : getTableImplementor());
            int modCount = -1;
            __APPLY_STEP__:
            while (modCount != modCount()) {
                modCount = modCount();
                if (selections != null) {
                    for (Selection<?> selection : selections) {
                        if (!visitor.isApplied(this, selection)) {
                            Ast.from(selection, astContext).accept(visitor);
                            if (modCount != modCount()) {
                                continue __APPLY_STEP__;
                            }
                            visitor.apply(this, selection);
                        }
                    }
                }
                for (Predicate predicate : getPredicates()) {
                    if (!visitor.isApplied(this, predicate)) {
                        ((Ast) predicate).accept(visitor);
                        if (modCount != modCount()) {
                            continue __APPLY_STEP__;
                        }
                        visitor.apply(this, predicate);
                    }
                }
                for (Expression<?> groupExpr : getGroupExpressions()) {
                    if (!visitor.isApplied(this, groupExpr)) {
                        ((Ast) groupExpr).accept(visitor);
                        if (modCount != modCount()) {
                            continue __APPLY_STEP__;
                        }
                        visitor.apply(this, groupExpr);
                    }
                }
                for (Predicate havingPredicate : getHavingPredicates()) {
                    if (!visitor.isApplied(this, havingPredicate)) {
                        ((Ast) havingPredicate).accept(visitor);
                        if (modCount != modCount()) {
                            continue __APPLY_STEP__;
                        }
                        visitor.apply(this, havingPredicate);
                    }
                }
                for (Order order : getOrders()) {
                    if (!visitor.isApplied(this, order)) {
                        ((Ast) order.getExpression()).accept(visitor);
                        if (modCount != modCount()) {
                            continue __APPLY_STEP__;
                        }
                        visitor.apply(this, order);
                    }
                }
            }
        } finally {
            astContext.popStatement();
        }
    }

    private void applyGlobalFilerImpl(ApplyFilterVisitor visitor, TableImplementor<?> table) {
        FilterLevel level = visitor.level;
        if (level == FilterLevel.IGNORE_ALL || filterPredicates.get(table) != null) {
            return;
        }
        Filter<Props> globalFilter;
        if (level == FilterLevel.IGNORE_USER_FILTERS) {
            globalFilter = getSqlClient().getFilters().getLogicalDeletedFilter(table.getImmutableType());
        } else {
            globalFilter = getSqlClient().getFilters().getFilter(table.getImmutableType());
        }
        if (globalFilter != null) {
            FilterArgsImpl<Props> args = new FilterArgsImpl<>(
                    table,
                    TableProxies.wrap(table),
                    false
            );
            globalFilter.filter(args);
            whereByFilter(table, args.toPredicates());
            modify();
        }
    }

    public void validateMutable() {
        if (frozen) {
            throw new IllegalStateException(
                    "Cannot mutate the statement because it has been frozen"
            );
        }
    }

    public JSqlClientImplementor getSqlClient() {
        return sqlClient;
    }

    @Override
    public Filterable where(Predicate ... predicates) {
        validateMutable();
        for (Predicate predicate : predicates) {
            if (predicate != null) {
                this.predicates.add(predicate);
                modify();
            }
        }
        return this;
    }

    public ExecutionPurpose getPurpose() {
        return getContext().getPurpose();
    }

    protected static List<Predicate> mergePredicates(List<Predicate> predicates) {
        if (predicates.size() < 2) {
            return predicates;
        }
        VirtualPredicateMergedResult.removeEmptyResult(predicates);
        return Collections.singletonList(
                Predicate.and(
                        predicates.toArray(EMPTY_PREDICATES)
                )
        );
    }

    public final int modCount() {
        return modCount;
    }

    protected final void modify() {
        modCount++;
        AbstractMutableStatementImpl parent = getParent();
        if (parent != null) {
            parent.modify();
        }
    }

    private static class ApplyFilterVisitor extends AstVisitor {

        final FilterLevel level;

        private final IdentityPairSet<AbstractMutableStatementImpl, Object> appliedSet = new IdentityPairSet<>();

        public ApplyFilterVisitor(AstContext ctx, FilterLevel level) {
            super(ctx);
            this.level = level;
        }

        @Override
        public boolean visitSubQuery(TypedSubQuery<?> subQuery) {
            if (subQuery instanceof ConfigurableSubQueryImpl<?>) {
                AbstractMutableStatementImpl statement = ((ConfigurableSubQueryImpl<?>)subQuery).getBaseQuery();
                FilterManager.executing(((MutableSubQueryImpl) statement).filterOwner(), () -> {
                    statement.applyGlobalFiltersImpl(this, null, null);
                });
                return false;
            }
            return true;
        }

        @Override
        public void visitTableReference(RealTable table, ImmutableProp prop, boolean rawId) {
            AstContext ctx = getAstContext();
            if (prop != null && prop.isId() && (
                    rawId || TableUtils.isRawIdAllowed(table.getTableImplementor(), ctx.getSqlClient()))
            ) {
                table = table.getParent();
            }
            while (table != null) {
                table
                        .getTableImplementor()
                        .getStatement()
                        .applyGlobalFilerImpl(this, table.getTableImplementor());
                table = table.getParent();
            }
        }

        public boolean isApplied(AbstractMutableStatementImpl statement, Selection<?> selection) {
            return appliedSet.has(statement, selection);
        }

        public boolean isApplied(AbstractMutableStatementImpl statement, Expression<?> expression) {
            return appliedSet.has(statement, expression);
        }

        public boolean isApplied(AbstractMutableStatementImpl statement, Order order) {
            return appliedSet.has(statement, order);
        }

        public boolean apply(AbstractMutableStatementImpl statement, Selection<?> selection) {
            return appliedSet.add(statement, selection);
        }

        public void apply(AbstractMutableStatementImpl statement, Expression<?> expression) {
            appliedSet.add(statement, expression);
        }

        public void apply(AbstractMutableStatementImpl statement, Order order) {
            appliedSet.add(statement, order);
        }
    }
}
