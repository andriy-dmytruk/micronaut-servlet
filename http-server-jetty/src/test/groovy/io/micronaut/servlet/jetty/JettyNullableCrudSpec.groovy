
package io.micronaut.servlet.jetty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Patch
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import io.micronaut.core.annotation.Nullable
import java.util.concurrent.atomic.AtomicLong

class JettyNullableCrudSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'JettyNullableCrudSpec'])

    void "test CRUD operations on generated client that returns blocking responses"() {
        given:
        NullableBookClient client = embeddedServer.applicationContext.getBean(NullableBookClient)

        when:
        NullableBook book = client.get(99)
        List<NullableBook> books = client.list()

        then:
        book == null
        books.size() == 0

        when:
        book = client.save("The Stand")

        then:
        book != null
        book.title == "The Stand"
        book.id == 1

        when:
        book = client.get(book.id)

        then:
        book != null
        book.title == "The Stand"
        book.id == 1

        when:
        book = client.update(book.id, "The Shining")

        then:
        book != null
        book.title == "The Shining"
        book.id == 1

        when:
        client.delete(book.id)

        then:
        client.get(book.id) == null
    }

    void "test DELETE operation with null values"() {
        given:
        NullableBookClient client = embeddedServer.applicationContext.getBean(NullableBookClient)

        when:
        client.delete(null)

        then:
        noExceptionThrown()
    }

    void "test POST operation with null values"() {
        given:
        NullableBookClient client = embeddedServer.applicationContext.getBean(NullableBookClient)

        when:
        NullableBook book = client.save(null)

        then:
        book.title == null
        noExceptionThrown()
    }

    void "test PUT operation with null values"() {
        given:
        NullableBookClient client = embeddedServer.applicationContext.getBean(NullableBookClient)

        when:
        NullableBook saved = client.save("Temporary")
        NullableBook book = client.update(saved.id, null)

        then:
        book.title == null
        noExceptionThrown()
    }

    void "test GET operation with null values"() {
        given:
        NullableBookClient client = embeddedServer.applicationContext.getBean(NullableBookClient)

        when:
        NullableBook book = client.get(null)

        then:
        book == null
        noExceptionThrown()
    }

    @Requires(property = 'spec.name', value = 'JettyNullableCrudSpec')
    @Client('/blocking/nullableBooks')
    static interface NullableBookClient extends NullableBookApi {
    }

    @Requires(property = 'spec.name', value = 'JettyNullableCrudSpec')
    @Controller("/blocking/nullableBooks")
    static class NullableBookController implements NullableBookApi {

        Map<Long, NullableBook> books = new LinkedHashMap<>()
        AtomicLong currentId = new AtomicLong(0)

        @Override
        NullableBook get(@Nullable Long id) {
            return books.get(id)
        }

        @Override
        List<NullableBook> list() {
            return books.values().toList()
        }

        @Override
        void delete(@Nullable Long id) {
            books.remove(id)
        }

        @Override
        NullableBook save(@Nullable String title) {
            NullableBook book = new NullableBook(title: title, id: currentId.incrementAndGet())
            books[book.id] = book
            return book
        }

        @Override
        NullableBook update(Long id, String title) {
            NullableBook book = books[id]
            if (book != null) {
                book.title = title
            }
            return book
        }
    }

    static interface NullableBookApi {

        @Get("/show{/id}") // /show to avoid calling list instead
        NullableBook get(@Nullable Long id)

        @Get
        List<NullableBook> list()

        @Delete("{/id}")
        void delete(@Nullable Long id)

        @Post
        NullableBook save(@Nullable String title)

        @Patch("/{id}")
        NullableBook update(Long id, @Nullable String title)
    }


    static class NullableBook {
        Long id
        String title
    }
}
