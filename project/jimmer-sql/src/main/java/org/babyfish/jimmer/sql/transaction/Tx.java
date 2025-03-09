package org.babyfish.jimmer.sql.transaction;

import org.babyfish.jimmer.sql.runtime.ConnectionManager;

import javax.sql.DataSource;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Jimmer supports compile-time transaction management AOP implementation.
 * If a class or its methods are annotated with {@link Tx}, Jimmer's
 * APT/KSP will automatically generate a derived class for it.
 *
 * <ul>
 *     <li>If an IOC framework is used, its implementation
 *     should be an encapsulation of the transaction management
 *     within the IOC framework. Taking {@code jimmer-spring-starter}
 *     as an example, it is the {@code SpringConnectionManager}
 *     which will be created and enabled automatically.</li>
 *
 *     <li>If no IOC framework is used, the class
 *     {@link AbstractTxConnectionManager} is the
 *     lightweight implementation provided by jimmer,
 *     please specify the connection manager of sqlClient by
 *     {@link ConnectionManager#simpleConnectionManager(DataSource)}</li>
 * </ul>
 *
 * <p>User writes code like this:</p>
 * <pre>{@code
 * @Tx(Propgation.REQUIRED)
 * public class BookRepository {
 *
 *     final JSqlClient sqlClient;
 *
 *     public BookService(JSqlClient sqlClient) {
 *         this.sqlClient = sqlClient;
 *     }
 *
 *     public List<Book> findBooks() {
 *         ... ...
 *     }
 *
 *     @Tx(Propgation.MANDATORY)
 *     public void saveBook(BookInput input) {
 *         ... ...
 *     }
 * }
 * }</pre>
 *
 * <p>After compilation, a derived class will be generated automatically:</p>
 * <pre>{@code
 * public class BookRepositoryTx extends BookRepository {
 *
 *     public BookService(JSqlClient sqlClient) {
 *         super(sqlClient);
 *     }
 *
 *     @Override
 *     public List<Book> findBooks() {
 *         return sqlClient.transaction(Propagation.REQUIRED, con -> {
 *             return super.findBooks();
 *         });
 *     }
 *
 *     @Override
 *     public void saveBook(BookInput input) {
 *         sqlClient.transaction(Propagation.MANDATORY, con -> {
 *             super.saveBook(input);
 *             return null;
 *         });
 *     }
 * }
 * }</pre>
 *
 * <p>Obviously,</p>
 *
 * <ul>
 *     <li>If you are not using an IOC framework, you should use
 *     the generated class {@code UserServiceTx} instead of
 *     the original class {@code UserService}</li>
 *
 *     <li>If you are using an IOC framework and replace its
 *     transaction AOP, you might want the IOC framework to manage
 *     the generated class {@code UserServiceTx} but not
 *     the original class {@code UserService}.
 *     To achieve this, you can use {@link TargetAnnotation}.</li>
 * </ul>
 *
 * @see TargetAnnotation
 */
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Tx {

    Propagation value() default Propagation.REQUIRED;
}
