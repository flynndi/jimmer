package org.babyfish.jimmer.sql.example.bll

import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.sql.example.dal.BookStoreRepository
import org.babyfish.jimmer.sql.example.model.BookStore
import org.babyfish.jimmer.sql.example.model.by
import org.babyfish.jimmer.sql.example.model.input.BookStoreInput
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

/**
 * A real project should be a three-tier architecture consisting
 * of repository, service, and controller.
 *
 * This demo has no business logic, its purpose is only to tell users
 * how to use jimmer with the <b>least</b> code. Therefore, this demo
 * does not follow this convention, and let services be directly
 * decorated by `@RestController`, not `@Service`.
 */
@RestController
@RequestMapping("/bookStore")
@Transactional
class BookStoreService(
    private val bookStoreRepository: BookStoreRepository
) {

    @GetMapping("/simpleList")
    fun findSimpleStores(): List<@FetchBy("SIMPLE_FETCHER") BookStore> =
        bookStoreRepository.findAll(SIMPLE_FETCHER) {
            asc(BookStore::name)
        }

    @GetMapping("/list")
    fun findStores(): List<@FetchBy("DEFAULT_FETCHER") BookStore> =
        bookStoreRepository.findAll(DEFAULT_FETCHER) {
            asc(BookStore::name)
        }

    @GetMapping("/{id}")
    fun findComplexStore(
        @PathVariable id: Long
    ): @FetchBy("COMPLEX_FETCHER") BookStore? =
        bookStoreRepository.findNullable(id, COMPLEX_FETCHER)

    @PutMapping
    fun saveBookStore(@RequestBody input: BookStoreInput): BookStore =
        bookStoreRepository.save(input)

    @DeleteMapping("{id}")
    fun deleteBookStore(@PathVariable id: Long) {
        bookStoreRepository.deleteById(id)
    }

    companion object {

        private val SIMPLE_FETCHER = newFetcher(BookStore::class).by {
            name()
        }

        private val DEFAULT_FETCHER = newFetcher(BookStore::class).by {
            allScalarFields()
        }

        private val COMPLEX_FETCHER = newFetcher(BookStore::class).by {
            allScalarFields()
            avgPrice()
            books {
                allScalarFields()
                tenant(false)
                authors {
                    allScalarFields()
                }
            }
        }
    }
}